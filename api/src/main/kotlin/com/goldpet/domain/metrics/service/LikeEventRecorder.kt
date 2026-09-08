package com.goldpet.domain.metrics.service

import com.goldpet.domain.experiment.service.ExperimentService
import com.goldpet.domain.metrics.entity.LikeEvent
import com.goldpet.domain.metrics.entity.LikeEventAction
import com.goldpet.domain.metrics.repository.LikeEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** like_events.source VARCHAR(32) — client-controlled @RequestParam 이므로 영속 전 잘라낸다(analytics-only). */
private const val SOURCE_MAX_LEN = 32

/**
 * like_events append 의 **격리된 트랜잭션 writer**(FIX-A #1, V80).
 *
 * [Propagation.REQUIRES_NEW] 로 caller(LikeService/MatchService.likeUser)의 @Transactional 을 **suspend** 하고
 * 별도 트랜잭션에서 insert 한다. 이렇게 해야 like_events insert 가 실제 DB 에서 실패해도
 * (cohort/action CHECK 위반, source 초과, transient error 등) outer 트랜잭션이 rollback-only 로
 * 오염되지 않아 **좋아요 자체가 롤백되지 않는다**(BadgeAwardService 와 동일한 버그 클래스).
 *
 * cohort 는 [ExperimentService.readCohortOrNone] 로 experiment_assignment 에서 **읽기만** 한다 —
 * 해시 재계산 금지(guardrail #3), 미할당(미노출) 유저는 NONE(A/B 분모 제외).
 *
 * 주의: 이 메서드의 INSERT 실패는 REQUIRES_NEW 경계(프록시)에서 던져지므로, best-effort 삼킴은
 * 이 메서드 **밖**( [LikeEventRecorder.record] )에서 해야 한다 — @Transactional 메서드 내부의
 * try/catch 는 commit 시점 예외를 잡지 못한다.
 */
@Service
class LikeEventTxWriter(
    private val likeEventRepository: LikeEventRepository,
    private val experimentService: ExperimentService
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persist(
        fromUserId: Long,
        toUserId: Long,
        action: LikeEventAction,
        isMatch: Boolean,
        source: String?
    ) {
        val cohort = experimentService.readCohortOrNone(fromUserId)
        likeEventRepository.save(
            LikeEvent(
                userId = fromUserId,
                targetUserId = toUserId,
                action = action.name,
                isMatch = isMatch,
                cohort = cohort,
                source = source?.take(SOURCE_MAX_LEN)
            )
        )
    }
}

/**
 * like_events append 단일 진입점(plan §1 W1(2), V80) — best-effort 래퍼.
 *
 * 좋아요 seam 두 곳(LikeService.likeUser/unlikeUser = ACTIVE 경로, MatchService.likeUser = 등록된 두 번째 경로)에서
 * 동일하게 호출하여 match-rate 퍼널 사실을 1행씩 기록한다(guardrail #1 — 두 seam 모두 계측).
 *
 * 실제 insert 는 [LikeEventTxWriter.persist] 가 REQUIRES_NEW 로 수행하므로(cross-bean 호출 → 프록시 적용),
 * 여기 try/catch 가 inner 트랜잭션 commit 시점 실패까지 잡아 사용자 향 좋아요 플로우를 보호한다.
 */
@Service
class LikeEventRecorder(
    private val likeEventTxWriter: LikeEventTxWriter
) {
    private val log = LoggerFactory.getLogger(LikeEventRecorder::class.java)

    /**
     * 좋아요/취소 이벤트 1행 append (best-effort).
     *
     * @param fromUserId 행위자(좋아요를 누른/취소한 유저) — cohort 는 이 유저 기준으로 read.
     * @param toUserId   대상 유저.
     * @param action     LIKE | CANCEL.
     * @param isMatch    그 시점에 THIS 좋아요가 매치를 만들었는지(point-in-time). CANCEL 은 항상 false.
     * @param source     발생 리스트/정렬 출처(compatible|distance|popular|received 등). nullable, 32자로 잘림.
     */
    fun record(
        fromUserId: Long,
        toUserId: Long,
        action: LikeEventAction,
        isMatch: Boolean,
        source: String?
    ) {
        try {
            likeEventTxWriter.persist(fromUserId, toUserId, action, isMatch, source)
        } catch (e: Exception) {
            log.warn(
                "like_events append failed (best-effort, like flow unaffected): from={} to={} action={} isMatch={}",
                fromUserId, toUserId, action, isMatch, e
            )
        }
    }
}
