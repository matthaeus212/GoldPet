package com.goldpet.domain.file.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.goldpet.domain.common.entity.FileAttachment
import com.goldpet.domain.common.repository.FileAttachmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class WebPBackfillServiceTest {

    private lateinit var amazonS3: AmazonS3
    private lateinit var fileAttachmentRepository: FileAttachmentRepository
    private lateinit var service: WebPBackfillService

    private val publicEndpoint = "http://s3.test"
    private val privateBucket = "goldpet-private"

    @BeforeEach
    fun setUp() {
        amazonS3 = mock()
        fileAttachmentRepository = mock()

        service = WebPBackfillService(
            amazonS3 = amazonS3,
            publicEndpoint = publicEndpoint,
            privateBucketName = privateBucket,
            fileAttachmentRepository = fileAttachmentRepository
        )
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Solid-colour 100×100 JPEG as bytes — small enough to be fast in tests. */
    private fun solidJpegBytes(width: Int = 100, height: Int = 100): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply { color = Color.CYAN; fillRect(0, 0, width, height); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(img, "JPEG", it) }.toByteArray()
    }

    private fun stubS3GetObject(bucket: String, key: String, jpegBytes: ByteArray) {
        val s3Object = mock<S3Object>()
        val inputStream = S3ObjectInputStream(ByteArrayInputStream(jpegBytes), null)
        whenever(s3Object.objectContent).thenReturn(inputStream)
        whenever(amazonS3.getObject(bucket, key)).thenReturn(s3Object)
    }

    private fun fakeRow(
        id: Long,
        thumbUrl: String = "$publicEndpoint/goldpet-public/${id}_thumb.jpg",
        medUrl: String? = "$publicEndpoint/goldpet-public/${id}_medium.jpg",
        viewerUrl: String? = "$publicEndpoint/goldpet-public/${id}_viewer.jpg"
    ) = FileAttachment(
        id = id,
        ownerUserId = 1L,
        fileType = "IMAGE",
        mimeType = "image/jpeg",
        url = "$publicEndpoint/goldpet-public/$id.jpg",
        thumbnailUrl = thumbUrl,
        mediumUrl = medUrl,
        viewerUrl = viewerUrl
    )

    // ── test cases ───────────────────────────────────────────────────────────

    @Test
    fun `dryRun=true returns stats without any S3 or DB writes`() {
        val rows = listOf(fakeRow(1L), fakeRow(2L), fakeRow(3L))
        whenever(fileAttachmentRepository.findImagesWithJpegVariantsButNoWebp(any()))
            .thenReturn(PageImpl(rows, Pageable.unpaged(), 10L))

        val result = service.backfillBatch(dryRun = true, batchSize = 100)

        assertEquals(10L, result.total)
        assertEquals(3, result.eligible)
        assertEquals(0, result.processed)
        assertEquals(0, result.succeeded)
        assertEquals(0, result.failed)
        assertTrue(result.mismatches.isEmpty())

        // No S3 or DB side-effects
        verify(amazonS3, never()).getObject(any<String>(), any<String>())
        verify(amazonS3, never()).putObject(any<String>(), any<String>(), any(), any())
        verify(fileAttachmentRepository, never()).updateWebpVariantUrls(any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `empty batch returns all-zero result`() {
        whenever(fileAttachmentRepository.findImagesWithJpegVariantsButNoWebp(any()))
            .thenReturn(PageImpl(emptyList(), Pageable.unpaged(), 0L))

        val result = service.backfillBatch(dryRun = false, batchSize = 100)

        assertEquals(0L, result.total)
        assertEquals(0, result.eligible)
        assertEquals(0, result.succeeded)
        assertEquals(0, result.failed)
        assertTrue(result.mismatches.isEmpty())
        verify(fileAttachmentRepository, never()).updateWebpVariantUrls(any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `partial failure — failed row added to mismatches, remaining rows continue`() {
        val jpegBytes = solidJpegBytes()
        val rows = listOf(fakeRow(10L), fakeRow(20L), fakeRow(30L))
        whenever(fileAttachmentRepository.findImagesWithJpegVariantsButNoWebp(any()))
            .thenReturn(PageImpl(rows, Pageable.unpaged(), 3L))

        // Row 10: S3 download for thumb succeeds
        stubS3GetObject("goldpet-public", "10_thumb.jpg", jpegBytes)
        stubS3GetObject("goldpet-public", "10_medium.jpg", jpegBytes)
        stubS3GetObject("goldpet-public", "10_viewer.jpg", jpegBytes)

        // Row 20: S3 download for thumb throws → simulates S3 error
        whenever(amazonS3.getObject("goldpet-public", "20_thumb.jpg"))
            .thenThrow(RuntimeException("S3 unavailable"))

        // Row 30: succeeds
        stubS3GetObject("goldpet-public", "30_thumb.jpg", jpegBytes)
        stubS3GetObject("goldpet-public", "30_medium.jpg", jpegBytes)
        stubS3GetObject("goldpet-public", "30_viewer.jpg", jpegBytes)

        val result = service.backfillBatch(dryRun = false, batchSize = 100)

        assertEquals(2, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(3, result.processed)
        assertEquals(listOf(20L), result.mismatches)

        // DB updated for successful rows only
        verify(fileAttachmentRepository).updateWebpVariantUrls(eq(10L), anyOrNull(), anyOrNull(), anyOrNull())
        verify(fileAttachmentRepository).updateWebpVariantUrls(eq(30L), anyOrNull(), anyOrNull(), anyOrNull())
        verify(fileAttachmentRepository, never()).updateWebpVariantUrls(eq(20L), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `already-backfilled rows are excluded by repository query (idempotent)`() {
        // The query only returns rows where thumbnail_url_webp IS NULL.
        // Simulate DB returning no rows (all already backfilled).
        whenever(fileAttachmentRepository.findImagesWithJpegVariantsButNoWebp(any()))
            .thenReturn(PageImpl(emptyList(), Pageable.unpaged(), 0L))

        val result = service.backfillBatch(dryRun = false, batchSize = 100)

        assertEquals(0L, result.total)
        assertEquals(0, result.succeeded)
        verify(amazonS3, never()).getObject(any<String>(), any<String>())
    }

    @Test
    fun `blank thumbnail_url causes mismatch — no S3 call and no DB update`() {
        // Simulates a stuck row whose thumbnail_url was stored as an empty string.
        // The service should detect null thumbWebpUrl and skip updateWebpVariantUrls,
        // preventing this row from looping forever in the next batch query.
        val row = fakeRow(id = 60L, thumbUrl = "")
        whenever(fileAttachmentRepository.findImagesWithJpegVariantsButNoWebp(any()))
            .thenReturn(PageImpl(listOf(row), Pageable.unpaged(), 1L))

        val result = service.backfillBatch(dryRun = false, batchSize = 100)

        assertEquals(0, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(listOf(60L), result.mismatches)
        verify(amazonS3, never()).getObject(any<String>(), any<String>())
        verify(fileAttachmentRepository, never()).updateWebpVariantUrls(any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `convertJpegToWebp returning null adds row to mismatches and skips DB update`() {
        // URL has only a bucket segment, no key — parseVariantUrl returns null → convertJpegToWebp returns null.
        // Service must add the row to mismatches and must NOT call updateWebpVariantUrls,
        // so thumbnail_url_webp stays null by design (not by a null-write that re-queues the row).
        val unparseable = "http://s3.test/only-bucket"
        val row = fakeRow(id = 50L, thumbUrl = unparseable)
        whenever(fileAttachmentRepository.findImagesWithJpegVariantsButNoWebp(any()))
            .thenReturn(PageImpl(listOf(row), Pageable.unpaged(), 1L))

        val result = service.backfillBatch(dryRun = false, batchSize = 100)

        assertEquals(0, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(listOf(50L), result.mismatches)
        verify(fileAttachmentRepository, never()).updateWebpVariantUrls(any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `successful run uploads WebP for all three variants and calls DB update`() {
        val jpegBytes = solidJpegBytes()
        val row = fakeRow(99L)
        whenever(fileAttachmentRepository.findImagesWithJpegVariantsButNoWebp(any()))
            .thenReturn(PageImpl(listOf(row), Pageable.unpaged(), 1L))

        stubS3GetObject("goldpet-public", "99_thumb.jpg", jpegBytes)
        stubS3GetObject("goldpet-public", "99_medium.jpg", jpegBytes)
        stubS3GetObject("goldpet-public", "99_viewer.jpg", jpegBytes)

        val result = service.backfillBatch(dryRun = false, batchSize = 100)

        assertEquals(1, result.succeeded)
        assertEquals(0, result.failed)

        // Three WebP PUTs (thumb, medium, viewer)
        verify(amazonS3).putObject(eq("goldpet-public"), eq("99_thumb.webp"), any(), any())
        verify(amazonS3).putObject(eq("goldpet-public"), eq("99_medium.webp"), any(), any())
        verify(amazonS3).putObject(eq("goldpet-public"), eq("99_viewer.webp"), any(), any())

        verify(fileAttachmentRepository).updateWebpVariantUrls(
            eq(99L),
            eq("$publicEndpoint/goldpet-public/99_thumb.webp"),
            eq("$publicEndpoint/goldpet-public/99_medium.webp"),
            eq("$publicEndpoint/goldpet-public/99_viewer.webp")
        )
    }

    @Test
    fun `convertJpegToWebp returns null for SVG URL — no S3 download or upload`() {
        // Repository query에서 이미 제외되지만 service 단 안전망 검증.
        val result = service.convertJpegToWebp("http://s3.test/goldpet-public/abc.svg")
        assertEquals(null, result)
        verify(amazonS3, never()).getObject(any<String>(), any<String>())
        verify(amazonS3, never()).putObject(any<String>(), any<String>(), any(), any())
    }
}
