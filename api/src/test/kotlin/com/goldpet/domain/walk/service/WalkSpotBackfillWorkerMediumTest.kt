// 구 산책사진의 600px `_medium` 변형 백필 동작을 검증하는 단위 테스트
package com.goldpet.domain.walk.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.goldpet.domain.walk.entity.WalkSpot
import com.goldpet.domain.walk.entity.WalkSpotType
import com.goldpet.domain.walk.repository.WalkSpotRepository
import org.apache.http.client.methods.HttpGet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.Optional
import javax.imageio.ImageIO

class WalkSpotBackfillWorkerMediumTest {

    private lateinit var walkSpotRepository: WalkSpotRepository
    private lateinit var amazonS3: AmazonS3
    private lateinit var worker: WalkSpotBackfillWorker

    private val bucket = "goldpet-private"
    private val rawKey = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"

    @BeforeEach
    fun setUp() {
        walkSpotRepository = mock()
        amazonS3 = mock()
        worker = WalkSpotBackfillWorker(walkSpotRepository, amazonS3, bucket)
    }

    /** 실제 디코딩 가능한 작은 JPEG 을 S3Object 로 흉내낸다. */
    private fun stubS3Image(width: Int = 1200, height: Int = 900) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpg", out)
        val s3Object = S3Object().apply {
            objectContent = S3ObjectInputStream(ByteArrayInputStream(out.toByteArray()), HttpGet())
        }
        whenever(amazonS3.getObject(eq(bucket), eq(rawKey))).thenReturn(s3Object)
    }

    private fun photoSpot(viewerKey: String?, mediumKey: String?, thumbKey: String?): WalkSpot {
        val gf = GeometryFactory(PrecisionModel(), 4326)
        return WalkSpot(
            id = 1L,
            walk = mock(),
            location = gf.createPoint(Coordinate(127.0, 37.0)),
            type = WalkSpotType.PHOTO,
            timestamp = LocalDateTime.now(),
            imageUrl = rawKey,
            note = null,
            imageKeyViewer = viewerKey,
            imageKeyMedium = mediumKey,
            imageKeyThumb = thumbKey
        )
    }

    // 구 사진: viewer/thumb 는 예전 워커가 만들어 있고 medium 만 없다.
    // → medium 만 생성하고(기존 변형 재생성 금지) imageKeyMedium 을 채워야 한다.
    @Test
    fun `generates only the missing medium variant for a legacy spot that already has viewer and thumb`() {
        val viewerKey = "a1b2c3d4-e5f6-7890-abcd-ef0123456789_viewer.jpg"
        val thumbKey = "a1b2c3d4-e5f6-7890-abcd-ef0123456789_thumb.jpg"
        val spot = photoSpot(viewerKey = viewerKey, mediumKey = null, thumbKey = thumbKey)
        whenever(walkSpotRepository.findById(1L)).thenReturn(Optional.of(spot))
        stubS3Image()

        val result = worker.processRow(1L, dryRun = false)

        assertEquals(WalkSpotBackfillWorker.ProcessResult.Outcome.PROCESSED, result.outcome)

        // `_medium.jpg` 단 하나만 업로드돼야 한다(기존 thumb/viewer 는 재생성하지 않음).
        val keyCaptor = argumentCaptor<String>()
        verify(amazonS3).putObject(eq(bucket), keyCaptor.capture(), any<java.io.InputStream>(), any())
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef0123456789_medium.jpg", keyCaptor.firstValue)

        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef0123456789_medium.jpg", spot.imageKeyMedium)
        // 기존 키는 보존
        assertEquals(viewerKey, spot.imageKeyViewer)
        assertEquals(thumbKey, spot.imageKeyThumb)
    }

    // viewer 와 medium 이 모두 있으면 이미 완료된 행이므로 건드리지 않는다(무한 sweep 방지).
    @Test
    fun `skips a spot that already has both viewer and medium`() {
        val spot = photoSpot(
            viewerKey = "x_viewer.jpg",
            mediumKey = "x_medium.jpg",
            thumbKey = "x_thumb.jpg"
        )
        whenever(walkSpotRepository.findById(1L)).thenReturn(Optional.of(spot))

        val result = worker.processRow(1L, dryRun = false)

        assertEquals(WalkSpotBackfillWorker.ProcessResult.Outcome.SKIPPED, result.outcome)
        verify(amazonS3, never()).getObject(any<String>(), any<String>())
        verify(amazonS3, never()).putObject(any<String>(), any<String>(), any<java.io.InputStream>(), any())
    }

    // 신규 사진(변형 키 전무): thumb/medium/viewer 3종을 모두 생성한다.
    @Test
    fun `generates all three variants for a fresh spot`() {
        val spot = photoSpot(viewerKey = null, mediumKey = null, thumbKey = null)
        whenever(walkSpotRepository.findById(1L)).thenReturn(Optional.of(spot))
        stubS3Image()

        val result = worker.processRow(1L, dryRun = false)

        assertEquals(WalkSpotBackfillWorker.ProcessResult.Outcome.PROCESSED, result.outcome)
        val keyCaptor = argumentCaptor<String>()
        verify(amazonS3, org.mockito.kotlin.times(3))
            .putObject(eq(bucket), keyCaptor.capture(), any<java.io.InputStream>(), any())
        val keys = keyCaptor.allValues
        assertEquals(true, keys.any { it.endsWith("_thumb.jpg") })
        assertEquals(true, keys.any { it.endsWith("_medium.jpg") })
        assertEquals(true, keys.any { it.endsWith("_viewer.jpg") })
        assertNotNull(spot.imageKeyMedium)
    }
}
