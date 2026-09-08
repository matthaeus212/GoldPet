package com.goldpet.domain.metrics.service

import com.goldpet.domain.experiment.service.ExperimentService
import com.goldpet.domain.metrics.entity.LikeEvent
import com.goldpet.domain.metrics.entity.LikeEventAction
import com.goldpet.domain.metrics.repository.LikeEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * LikeEventTxWriter 단위 테스트 — persist 로직(필드/코호트/source 절단)(V80, FIX-A #2).
 * - 코호트는 ExperimentService.readCohortOrNone 에서 READ(재계산 금지, guardrail #3).
 * - 할당 유저 → 그 코호트, 미할당 유저 → NONE.
 * - LIKE/CANCEL action + point-in-time is_match.
 * - source 는 VARCHAR(32) 로 영속 전 32자 절단.
 */
class LikeEventTxWriterTest {

    @Mock
    private lateinit var likeEventRepository: LikeEventRepository

    @Mock
    private lateinit var experimentService: ExperimentService

    private lateinit var writer: LikeEventTxWriter

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        writer = LikeEventTxWriter(likeEventRepository, experimentService)
    }

    @Test
    fun `LIKE writes exactly one row with the assigned user's cohort`() {
        whenever(experimentService.readCohortOrNone(1L)).thenReturn(ExperimentService.COHORT_TREATMENT)

        writer.persist(1L, 2L, LikeEventAction.LIKE, isMatch = false, source = "compatible")

        val captor = argumentCaptor<LikeEvent>()
        verify(likeEventRepository).save(captor.capture())
        with(captor.firstValue) {
            assertEquals(1L, userId)
            assertEquals(2L, targetUserId)
            assertEquals(LikeEventAction.LIKE.name, action)
            assertEquals(false, isMatch)
            assertEquals(ExperimentService.COHORT_TREATMENT, cohort)
            assertEquals("compatible", source)
        }
    }

    @Test
    fun `LIKE from an unassigned user records cohort NONE`() {
        // readCohortOrNone returns NONE for a user never exposed to the compatible sort.
        whenever(experimentService.readCohortOrNone(99L)).thenReturn(ExperimentService.COHORT_NONE)

        writer.persist(99L, 2L, LikeEventAction.LIKE, isMatch = false, source = "distance")

        val captor = argumentCaptor<LikeEvent>()
        verify(likeEventRepository).save(captor.capture())
        assertEquals(ExperimentService.COHORT_NONE, captor.firstValue.cohort)
    }

    @Test
    fun `is_match is recorded true when this like produced a match`() {
        whenever(experimentService.readCohortOrNone(1L)).thenReturn(ExperimentService.COHORT_CONTROL)

        writer.persist(1L, 2L, LikeEventAction.LIKE, isMatch = true, source = null)

        val captor = argumentCaptor<LikeEvent>()
        verify(likeEventRepository).save(captor.capture())
        assertEquals(true, captor.firstValue.isMatch)
        assertEquals(ExperimentService.COHORT_CONTROL, captor.firstValue.cohort)
    }

    @Test
    fun `CANCEL writes a CANCEL row`() {
        whenever(experimentService.readCohortOrNone(1L)).thenReturn(ExperimentService.COHORT_TREATMENT)

        writer.persist(1L, 2L, LikeEventAction.CANCEL, isMatch = false, source = null)

        val captor = argumentCaptor<LikeEvent>()
        verify(likeEventRepository).save(captor.capture())
        assertEquals(LikeEventAction.CANCEL.name, captor.firstValue.action)
    }

    @Test
    fun `source longer than 32 chars is truncated before persist (VARCHAR(32) guard)`() {
        whenever(experimentService.readCohortOrNone(1L)).thenReturn(ExperimentService.COHORT_TREATMENT)
        val overLong = "a".repeat(64)

        writer.persist(1L, 2L, LikeEventAction.LIKE, isMatch = false, source = overLong)

        val captor = argumentCaptor<LikeEvent>()
        verify(likeEventRepository).save(captor.capture())
        assertEquals(32, captor.firstValue.source?.length)
        assertTrue(overLong.startsWith(captor.firstValue.source!!))
    }

    @Test
    fun `does not recompute the hash - only reads cohort from experiment service`() {
        whenever(experimentService.readCohortOrNone(1L)).thenReturn(ExperimentService.COHORT_TREATMENT)

        writer.persist(1L, 2L, LikeEventAction.LIKE, isMatch = false, source = null)

        // guardrail #3: cohort is READ from the assignment service, never re-bucketed/written here.
        verify(experimentService).readCohortOrNone(1L)
        verify(experimentService, never()).getOrAssignCohort(any(), any())
    }
}
