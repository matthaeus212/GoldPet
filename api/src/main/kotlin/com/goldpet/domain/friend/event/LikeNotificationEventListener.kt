// 좋아요/매칭 알림 이벤트를 커밋 후 비동기로 처리해 FCM 발송을 트랜잭션 밖으로 분리하는 리스너
package com.goldpet.domain.friend.event

import com.goldpet.domain.notification.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * PERF-006 — [LikeNotificationEvent] 를 AFTER_COMMIT + @Async 로 받아 알림을 저장/발송한다.
 *
 * ## 실행 모델 (chat/walk 알림 리스너 선례와 동일)
 * - `@TransactionalEventListener(phase = AFTER_COMMIT)` — `LikeService.likeUser` 의 DB 커밋 이후에만 실행.
 *   like/match 저장이 롤백되면 알림도 발생하지 않음.
 * - `@Async("friendNotificationExecutor")` — 별도 풀에서 처리해 좋아요 hot path 와 FCM 송신을 디커플.
 *
 * ## best-effort
 * `notificationService.createNotification` 은 내부 `@Transactional` 로 자체 트랜잭션을 개설한다
 * (Notification save + FCM 단일 tx). 발송/저장 실패는 로그만 남기고 드롭 — 이미 커밋된 like 트랜잭션에는
 * 영향이 없다.
 */
@Component
class LikeNotificationEventListener(
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("friendNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLikeNotification(event: LikeNotificationEvent) {
        try {
            notificationService.createNotification(event.request)
        } catch (e: Exception) {
            log.warn(
                "Like/match notification failed (best-effort, dropped): toUserId={} type={}: {}",
                event.request.userId, event.request.type, e.message
            )
        }
    }
}
