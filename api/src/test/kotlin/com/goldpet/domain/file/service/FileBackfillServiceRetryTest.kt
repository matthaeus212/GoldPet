package com.goldpet.domain.file.service

import com.amazonaws.services.s3.AmazonS3
import com.goldpet.domain.common.entity.FileAttachment
import com.goldpet.domain.common.repository.FileAttachmentRepository
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.walk.service.WalkSpotBackfillWorker
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.time.LocalDateTime

/**
 * ARCH-004: 백필이 FileService 에서 FileBackfillService 로 분리되면서, 이 회귀 테스트도
 * 로직을 따라 이동한다(커버리지 유실 없음).
 */
class FileBackfillServiceRetryTest {

    private lateinit var amazonS3: AmazonS3
    private lateinit var fileAttachmentRepository: FileAttachmentRepository
    private lateinit var walkSpotBackfillWorker: WalkSpotBackfillWorker
    private lateinit var retryWorker: FileAttachmentRetryWorker
    private lateinit var systemSettingService: SystemSettingService
    private lateinit var fileService: FileService
    private lateinit var backfillService: FileBackfillService

    @BeforeEach
    fun setUp() {
        amazonS3 = mock()
        fileAttachmentRepository = mock()
        walkSpotBackfillWorker = mock()
        retryWorker = mock()
        systemSettingService = mock()
        fileService = mock()

        backfillService = FileBackfillService(
            amazonS3 = amazonS3,
            publicBucketName = "goldpet-public",
            publicEndpoint = "http://s3.test",
            fileAttachmentRepository = fileAttachmentRepository,
            fileService = fileService,
            walkSpotBackfillWorker = walkSpotBackfillWorker,
            fileAttachmentRetryWorker = retryWorker,
            systemSettingService = systemSettingService
        )
    }

    private fun fakeRow(id: Long) = FileAttachment(
        id = id,
        ownerUserId = 1L,
        fileType = "IMAGE",
        mimeType = "image/jpeg",
        url = "http://s3.test/goldpet-public/$id.jpg",
        thumbnailUrl = "http://s3.test/goldpet-public/$id.jpg", // skip-marked row
        mediumUrl = "http://s3.test/goldpet-public/$id.jpg"
    )

    @Test
    fun `retry sweep short-circuits when flag is off`() {
        whenever(systemSettingService.getBoolean(eq(FileBackfillService.RETRY_ENABLED_KEY), any())).thenReturn(false)

        val result = backfillService.backfillFileAttachmentsRetry(batchSize = 50, dryRun = false)

        assertEquals(0L, result.total)
        assertEquals(0, result.processed)
        verify(fileAttachmentRepository, never()).findImagesForRetry(any(), any())
        verify(retryWorker, never()).processRow(any(), any())
    }

    @Test
    fun `retry sweep dispatches each candidate row to worker and tallies outcomes`() {
        whenever(systemSettingService.getBoolean(eq(FileBackfillService.RETRY_ENABLED_KEY), any())).thenReturn(true)
        val rows = listOf(fakeRow(1L), fakeRow(2L), fakeRow(3L))
        whenever(fileAttachmentRepository.findImagesForRetry(any(), any()))
            .thenReturn(PageImpl(rows, Pageable.unpaged(), rows.size.toLong()))
        whenever(retryWorker.processRow(eq(1L), any())).thenReturn(FileAttachmentRetryWorker.Outcome.PROCESSED)
        whenever(retryWorker.processRow(eq(2L), any())).thenReturn(FileAttachmentRetryWorker.Outcome.SKIPPED)
        whenever(retryWorker.processRow(eq(3L), any())).thenReturn(FileAttachmentRetryWorker.Outcome.FAILED)

        val result = backfillService.backfillFileAttachmentsRetry(batchSize = 50, dryRun = false)

        assertEquals(3L, result.total)
        assertEquals(1, result.processed)
        assertEquals(1, result.skipped)
        assertEquals(1, result.failed)
        verify(retryWorker).processRow(1L, false)
        verify(retryWorker).processRow(2L, false)
        verify(retryWorker).processRow(3L, false)
    }

    @Test
    fun `dryRun propagates to worker and still returns processed count`() {
        whenever(systemSettingService.getBoolean(eq(FileBackfillService.RETRY_ENABLED_KEY), any())).thenReturn(true)
        val rows = listOf(fakeRow(10L))
        whenever(fileAttachmentRepository.findImagesForRetry(any(), any()))
            .thenReturn(PageImpl(rows, Pageable.unpaged(), 1L))
        whenever(retryWorker.processRow(eq(10L), eq(true)))
            .thenReturn(FileAttachmentRetryWorker.Outcome.PROCESSED)

        val result = backfillService.backfillFileAttachmentsRetry(batchSize = 10, dryRun = true)

        assertEquals(1L, result.total)
        assertEquals(1, result.processed)
        verify(retryWorker).processRow(10L, true)
    }

    @Test
    fun `stopBackfillRetry flips the feature flag to false`() {
        backfillService.stopBackfillRetry()

        verify(systemSettingService).setValue(
            eq(FileBackfillService.RETRY_ENABLED_KEY),
            eq("false"),
            anyOrNull()
        )
    }

    @Test
    fun `cutoff passed to repository is 7 days in the past`() {
        whenever(systemSettingService.getBoolean(eq(FileBackfillService.RETRY_ENABLED_KEY), any())).thenReturn(true)
        whenever(fileAttachmentRepository.findImagesForRetry(any(), any()))
            .thenReturn(PageImpl(emptyList(), Pageable.unpaged(), 0L))

        val before = LocalDateTime.now().minusDays(7).minusSeconds(5)
        backfillService.backfillFileAttachmentsRetry(batchSize = 1, dryRun = false)
        val after = LocalDateTime.now().minusDays(7).plusSeconds(5)

        val captor = org.mockito.kotlin.argumentCaptor<LocalDateTime>()
        verify(fileAttachmentRepository).findImagesForRetry(captor.capture(), any())
        val cutoff = captor.firstValue
        assert(cutoff.isAfter(before) && cutoff.isBefore(after)) {
            "cutoff must be ~now-7d, got $cutoff"
        }
    }
}
