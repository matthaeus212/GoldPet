package com.goldpet.domain.file.service

import com.amazonaws.services.s3.AmazonS3
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.GpsDirectory
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.repository.FileAttachmentRepository
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.walk.service.WalkSpotBackfillWorker
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockMultipartFile
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.multipart.MultipartFile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import javax.imageio.ImageIO

class FileServiceTest {

    private lateinit var amazonS3: AmazonS3
    private lateinit var fileAttachmentRepository: FileAttachmentRepository
    private lateinit var variantExecutor: ThreadPoolTaskExecutor
    private lateinit var walkSpotBackfillWorker: WalkSpotBackfillWorker
    private lateinit var fileAttachmentRetryWorker: FileAttachmentRetryWorker
    private lateinit var systemSettingService: SystemSettingService
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var fileService: FileService

    private val publicBucket = "goldpet-public-test"
    private val privateBucket = "goldpet-private-test"

    @BeforeEach
    fun setUp() {
        amazonS3 = mock()
        fileAttachmentRepository = mock()
        variantExecutor = mock()
        walkSpotBackfillWorker = mock()
        fileAttachmentRetryWorker = mock()
        systemSettingService = mock()
        meterRegistry = SimpleMeterRegistry()

        whenever(amazonS3.doesBucketExistV2(any())).thenReturn(true)

        fileService = FileService(
            amazonS3 = amazonS3,
            publicBucketName = publicBucket,
            privateBucketName = privateBucket,
            publicEndpoint = "http://s3.test",
            s3Endpoint = "http://s3.test",
            fileAttachmentRepository = fileAttachmentRepository,
            variantExecutor = variantExecutor,
            meterRegistry = meterRegistry,
            systemSettingService = systemSettingService,
            environment = MockEnvironment()
        )
    }

    // SEC-005 — presigned 만료 상한(MAX_PRESIGN_MINUTES=360)을 서버가 강제하는지 검증.
    @Test
    fun `getPresignedUrl 은 과도한 expirationMinutes 를 상한으로 clamp 한다`() {
        val captor = org.mockito.kotlin.argumentCaptor<com.amazonaws.services.s3.model.GeneratePresignedUrlRequest>()
        whenever(amazonS3.generatePresignedUrl(captor.capture()))
            .thenReturn(java.net.URL("http://s3.test/$privateBucket/key.jpg?sig=x"))

        val before = System.currentTimeMillis()
        fileService.getPresignedUrl("key.jpg", expirationMinutes = 100_000L)

        val requestedExpiry = captor.firstValue.expiration.time
        val maxAllowed = before + FileService.MAX_PRESIGN_MINUTES * 60 * 1000 + 5_000
        assertTrue(
            requestedExpiry <= maxAllowed,
            "요청 만료($requestedExpiry)는 상한($maxAllowed) 이내여야 한다",
        )
    }

    private fun solidImage(width: Int, height: Int): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.GRAY
        g.fillRect(0, 0, width, height)
        g.dispose()
        return img
    }

    private fun cleanJpeg(): ByteArray {
        val baos = ByteArrayOutputStream()
        ImageIO.write(solidImage(16, 16), "jpg", baos)
        return baos.toByteArray()
    }

    /**
     * 정상 JPEG 에 최소 GPS EXIF(APP1) 세그먼트를 주입한 픽스처.
     * little-endian TIFF: IFD0 → GPSInfo(0x8825) 포인터 → GPS IFD(GPSVersionID 2.3.0.0).
     * metadata-extractor 가 비어있지 않은 GpsDirectory 로 인식해야 strip 경로(re-encode)가 동작한다.
     */
    private fun jpegWithGpsExif(): ByteArray {
        val tiff = byteArrayOf(
            0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,                  // TIFF header(II), IFD0 @8
            0x01, 0x00,                                                      // IFD0: 1 entry
            0x25.toByte(), 0x88.toByte(), 0x04, 0x00, 0x01, 0x00, 0x00, 0x00, // tag=GPSInfo(0x8825), LONG, count1
            0x1A, 0x00, 0x00, 0x00,                                          // → GPS IFD @0x1A
            0x00, 0x00, 0x00, 0x00,                                          // IFD0 next = 0
            0x01, 0x00,                                                      // GPS IFD: 1 entry
            0x00, 0x00, 0x01, 0x00, 0x04, 0x00, 0x00, 0x00,                  // tag=GPSVersionID(0x0000), BYTE, count4
            0x02, 0x03, 0x00, 0x00,                                         // value = 2.3.0.0
            0x00, 0x00, 0x00, 0x00                                          // GPS IFD next = 0
        )
        // EXIF identifier = 'E','x','i','f', 0x00, 0x00 (NULL 2바이트 필수).
        val exifData = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) + tiff
        val len = exifData.size + 2
        val app1 = byteArrayOf(
            0xFF.toByte(), 0xE1.toByte(),
            (len shr 8 and 0xFF).toByte(), (len and 0xFF).toByte(),         // JPEG segment length (big-endian)
        ) + exifData
        val clean = cleanJpeg()
        // SOI + APP1(EXIF) + (clean JPEG without its leading SOI)
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + app1 + clean.copyOfRange(2, clean.size)
    }

    private fun invokeStripImageMetadata(bytes: ByteArray, mimeType: String): ByteArray {
        val m = FileService::class.java.getDeclaredMethod(
            "stripImageMetadata", ByteArray::class.java, String::class.java
        )
        m.isAccessible = true
        return m.invoke(fileService, bytes, mimeType) as ByteArray
    }

    @Test
    fun `generateVariantsRaw produces thumb medium and viewer for portrait 900x1600`() {
        val image = solidImage(900, 1600)

        fileService.generateVariantsRaw(
            attachmentId = 1L,
            image = image,
            baseFilename = "uuid-portrait",
            mimeType = "image/jpeg",
            bucket = publicBucket,
            isPublic = true,
            category = "community"
        )

        verify(amazonS3).putObject(eq(publicBucket), eq("uuid-portrait_thumb.jpg"), any(), any())
        verify(amazonS3).putObject(eq(publicBucket), eq("uuid-portrait_medium.jpg"), any(), any())
        verify(amazonS3).putObject(eq(publicBucket), eq("uuid-portrait_viewer.jpg"), any(), any())
        verify(fileAttachmentRepository).updateAllVariantUrlsWithWebp(
            eq(1L), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), eq(900), eq(1600)
        )

        val successCount = meterRegistry.counter(
            "file_variant_generate_total",
            "result", "success",
            "category", "community"
        ).count()
        assertEquals(1.0, successCount, "success counter must increment once for portrait jpeg")
    }

    @Test
    fun `generateVariantsRaw produces all three variants for 200x200 png when previously skipped`() {
        // Before T0-1 fix, shortSide=200 <= every maxDimension so every variant was skipped
        // and the row fell back to the original PNG. With always-generate, Thumbnailator
        // re-encodes to JPEG at source dimensions — the ~12x size win preserved.
        val image = solidImage(200, 200)

        fileService.generateVariantsRaw(
            attachmentId = 2L,
            image = image,
            baseFilename = "uuid-small",
            mimeType = "image/png",
            bucket = publicBucket,
            isPublic = true,
            category = "badge"
        )

        verify(amazonS3).putObject(eq(publicBucket), eq("uuid-small_thumb.jpg"), any(), any())
        verify(amazonS3).putObject(eq(publicBucket), eq("uuid-small_medium.jpg"), any(), any())
        verify(amazonS3).putObject(eq(publicBucket), eq("uuid-small_viewer.jpg"), any(), any())
        verify(fileAttachmentRepository).updateAllVariantUrlsWithWebp(
            eq(2L), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), eq(200), eq(200)
        )
    }

    @Test
    fun `generateVariantsRaw skips gif and records skip outcome`() {
        val image = solidImage(400, 400)

        fileService.generateVariantsRaw(
            attachmentId = 3L,
            image = image,
            baseFilename = "uuid-gif",
            mimeType = "image/gif",
            bucket = publicBucket,
            isPublic = true,
            category = "chat"
        )

        verify(amazonS3, never()).putObject(any<String>(), any<String>(), any(), any())

        val skipCount = meterRegistry.counter(
            "file_variant_generate_total",
            "result", "skip",
            "category", "chat"
        ).count()
        assertEquals(1.0, skipCount)
    }

    @Test
    fun `file_presigned_expired_fallback_total counter is pre-registered at construction`() {
        val counter = meterRegistry.find("file_presigned_expired_fallback_total").counter()
        assertNotNull(counter, "counter must be registered in init for Prometheus visibility")
        assertEquals(0.0, counter!!.count())
    }

    @Test
    fun `file_variant_generate_latency timer records sample on success path`() {
        val image = solidImage(800, 600)

        fileService.generateVariantsRaw(
            attachmentId = 4L,
            image = image,
            baseFilename = "uuid-latency",
            mimeType = "image/jpeg",
            bucket = publicBucket,
            isPublic = true,
            category = "profile"
        )

        val timer = meterRegistry.find("file_variant_generate_latency")
            .tag("category", "profile")
            .timer()
        assertNotNull(timer, "latency timer must be registered with category=profile tag")
        assertEquals(1L, timer!!.count())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // V68 BLOCKER #6 — EXIF GPS strip 경로 단위 테스트
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `stripImageMetadata removes GPS EXIF from JPEG`() {
        val jpegWithGps = jpegWithGpsExif()

        // 픽스처 자체 검증 — strip 이전엔 GPS 디렉터리가 존재해야 (없으면 픽스처 결함).
        val before = ImageMetadataReader.readMetadata(ByteArrayInputStream(jpegWithGps))
            .getFirstDirectoryOfType(GpsDirectory::class.java)
        assertNotNull(before, "fixture must contain a GPS EXIF directory before strip")
        assertFalse(before!!.isEmpty, "fixture GPS directory must be non-empty (else strip is skipped)")

        val stripped = invokeStripImageMetadata(jpegWithGps, "image/jpeg")

        assertFalse(stripped.contentEquals(jpegWithGps), "stripped JPEG must be re-encoded (bytes differ)")
        val after = ImageMetadataReader.readMetadata(ByteArrayInputStream(stripped))
            .getFirstDirectoryOfType(GpsDirectory::class.java)
        assertTrue(after == null || after.isEmpty, "GPS metadata must be gone after strip")
    }

    @Test
    fun `stripImageMetadata is a no-op for clean JPEG without GPS`() {
        val clean = cleanJpeg()
        val result = invokeStripImageMetadata(clean, "image/jpeg")
        assertTrue(
            result.contentEquals(clean),
            "clean image (no GPS) must be returned unchanged — no needless re-encode",
        )
    }

    @Test
    fun `validateImageFile rejects HEIC upload`() {
        val m = FileService::class.java.getDeclaredMethod("validateImageFile", MultipartFile::class.java)
        m.isAccessible = true
        val heic = MockMultipartFile("file", "photo.heic", "image/heic", byteArrayOf(0x00, 0x01, 0x02))

        val ex = assertThrows<InvocationTargetException> { m.invoke(fileService, heic) }
        assertTrue(
            ex.targetException is BadRequestException,
            "HEIC 는 허용 MIME 목록에 없어 업로드 단계에서 거부되어야 한다 (실제: ${ex.targetException})",
        )
    }
}
