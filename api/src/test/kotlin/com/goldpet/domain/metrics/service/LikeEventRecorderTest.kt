package com.goldpet.domain.metrics.service

import com.goldpet.domain.metrics.entity.LikeEventAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * LikeEventRecorder 단위 테스트 — best-effort 래퍼 계약(FIX-A #1).
 *
 * 실제 insert/cohort read 는 [LikeEventTxWriter] (REQUIRES_NEW) 가 담당하므로 여기선 mock 한다.
 * 핵심: writer.persist 가 던져도(=inner 트랜잭션 commit 실패) record() 는 **예외를 전파하지 않는다**
 * → 사용자 향 좋아요 플로우가 metrics 실패로 깨지지 않음(rollback-only 오염 방지의 마지막 안전망).
 *
 * 행 필드/코호트/source 절단 검증은 [LikeEventTxWriterTest] (실제 persist 로직)에서 수행.
 */
class LikeEventRecorderTest {

    @Mock
    private lateinit var likeEventTxWriter: LikeEventTxWriter

    private lateinit var recorder: LikeEventRecorder

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        recorder = LikeEventRecorder(likeEventTxWriter)
    }

    @Test
    fun `record delegates to the REQUIRES_NEW writer with the given args`() {
        recorder.record(1L, 2L, LikeEventAction.LIKE, isMatch = true, source = "compatible")

        verify(likeEventTxWriter).persist(1L, 2L, LikeEventAction.LIKE, true, "compatible")
    }

    @Test
    fun `record is best-effort - writer failure does NOT propagate (like flow protected)`() {
        whenever(
            likeEventTxWriter.persist(any(), any(), any(), any(), anyOrNull())
        ).thenThrow(RuntimeException("inner tx commit failed (e.g. CHECK violation)"))

        // must NOT throw — a like_events failure cannot roll back / break the like itself.
        recorder.record(1L, 2L, LikeEventAction.LIKE, isMatch = false, source = null)

        verify(likeEventTxWriter).persist(eq(1L), eq(2L), eq(LikeEventAction.LIKE), eq(false), anyOrNull())
    }

    @Test
    fun `CANCEL is delegated as a CANCEL action`() {
        recorder.record(1L, 2L, LikeEventAction.CANCEL, isMatch = false, source = null)

        verify(likeEventTxWriter).persist(1L, 2L, LikeEventAction.CANCEL, false, null)
    }
}
