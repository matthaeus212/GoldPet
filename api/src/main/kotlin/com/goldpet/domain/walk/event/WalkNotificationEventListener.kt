package com.goldpet.domain.walk.event

import com.goldpet.domain.gamification.streak.dto.StreakResult
import com.goldpet.domain.gamification.streak.service.StreakService
import com.goldpet.domain.notification.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 산책 완료: 스트릭 갱신 + 보상-요약 푸시 단일 리스너 (해석 A + W2a).
 *
 * ## 실행 모델 (단일 순서 리스너 — plan §2.2, Critic M3)
 * `@TransactionalEventListener(phase = AFTER_COMMIT)` — `WalkService.createWalk` DB 커밋 이후에만 실행.
 * 산책 저장/골드 적립이 롤백되면 스트릭 갱신·푸시 모두 발생하지 않음.
 * `@Async("walkNotificationExecutor")` — 별도 풀에서 처리해 walks_complete p95 hot path 와 디커플
 * (별도 streak @Async **푸시** 리스너를 두지 않으므로 중복 푸시·순서 경합 없음 — plan 리스크표).
 *
 * 리스너 내부 **동기 순서**:
 *  1. 스트릭 write — `StreakService.recordWalk` (`REQUIRES_NEW`, CourseWalkEventListener 선례).
 *  2. 커밋된 새 스트릭 값을 읽어(반환된 [StreakResult]),
 *  3. walk-완료 푸시와 **병합**(한 산책=한 푸시). 별도 @Async streak 푸시 리스너 금지(중복 방지).
 *
 * ## 게이팅
 * 스트릭은 모든 적격(저장된) 산책에 대해 갱신. 푸시는 `goldReward > 0` 일 때만 발송(기존 정책 유지).
 *
 * ## best-effort
 * 스트릭/푸시 실패는 로그만 남기고 드롭 — 보상은 이미 커밋된 산책 tx 에서 적립됨(부가 처리).
 */
@Component
class WalkNotificationEventListener(
    private val notificationService: NotificationService,
    private val streakService: StreakService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("walkNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onWalkCompleted(event: WalkCompletedEvent) {
        // 1) 스트릭 write (REQUIRES_NEW) → 2) 커밋된 값 read.
        val streak: StreakResult? = try {
            streakService.recordWalk(event.userId)
        } catch (e: Exception) {
            log.warn(
                "Streak update failed (best-effort, dropped): walkId={} userId={}: {}",
                event.walkId, event.userId, e.message
            )
            null
        }

        // 3) 보상-요약 + 스트릭 병합 푸시 (한 산책=한 푸시). 보상 없는 산책은 알리지 않음.
        if (event.goldReward <= 0) return
        try {
            notificationService.notifyWalkCompleted(
                userId = event.userId,
                walkId = event.walkId,
                distanceKm = event.distanceKm,
                goldReward = event.goldReward,
                streakDays = streak?.currentStreak
            )
        } catch (e: Exception) {
            log.warn(
                "Walk-completed push failed (best-effort, dropped): walkId={} userId={}: {}",
                event.walkId, event.userId, e.message
            )
        }
    }
}
