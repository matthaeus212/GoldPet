package com.goldpet.domain.chat.event

import com.goldpet.domain.chat.metrics.ChatNotificationMetrics
import com.goldpet.domain.metrics.ChatLatencyMetrics
import com.goldpet.domain.notification.service.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * T-chat-latency-v2 Step 2 — `ChatMessageSavedEvent` async FCM fan-out 리스너.
 *
 * ## 활성 조건
 * `goldpet.chat.async.enabled=true` 일 때만 빈 등록 (기본 false). prod yml 은 미설정 유지 →
 * 기존 동기 경로 (ChatService 내부 inline notification loop) fallback.
 *
 * ## 실행 모델
 * - `@TransactionalEventListener(phase = AFTER_COMMIT)` — ChatService.saveChatMessage 의 DB 커밋 이후에만 실행 → 유실 없는 fan-out.
 * - `@Async("chatNotificationExecutor")` — core=4/max=16/queue=500 풀에서 비동기 처리 → STOMP broadcast hot path 와 FCM 송신 디커플.
 *
 * ## 재시도 정책 (team-lead Task #5 보정 사양)
 * - FCM 1차 예외 → `chat.fcm.failure` 증가 → 500ms 후 1회 재시도
 * - 재시도도 실패 → `chat.fcm.dropped` 증가 후 영구 드롭 (루프/재큐잉 금지)
 *
 * ## 지연 계측
 * Listener 전체 실행 시간은 `chat.send.server_latency_ms{phase=post_async}` 에 기록.
 * Step 0 preregister 덕에 phase 라벨은 startup 부터 노출됨.
 *
 * ## 트랜잭션 경계
 * `@Async` 실행 스레드는 호출자 트랜잭션 컨텍스트를 공유하지 않음. `notificationService.notifyMessage` 는
 * 내부 `@Transactional` 로 자체 트랜잭션 개설 — Notification save + FCM 이 단일 tx 안에서 수행되므로
 * FCM 실패 시 해당 수신자 DB 기록도 롤백된다 (기존 동작 유지). 재시도 경로가 이 손실을 1회 보정.
 */
@Component
@ConditionalOnProperty(
    name = ["goldpet.chat.async.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class ChatNotificationEventListener(
    private val notificationService: NotificationService,
    private val chatLatencyMetrics: ChatLatencyMetrics,
    private val chatNotificationMetrics: ChatNotificationMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("chatNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleChatMessageSaved(event: ChatMessageSavedEvent) {
        if (event.recipientUserIds.isEmpty()) return

        chatLatencyMetrics.recordServerLatency(ChatLatencyMetrics.Phase.POST_ASYNC) {
            log.debug(
                "Chat async fan-out start: messageId={} roomId={} recipients={} clientMsgId={}",
                event.messageId, event.roomId, event.recipientUserIds.size, event.clientMsgId,
            )
            event.recipientUserIds.forEach { recipientUserId ->
                sendWithSingleRetry(recipientUserId, event)
            }
        }
    }

    private fun sendWithSingleRetry(recipientUserId: Long, event: ChatMessageSavedEvent) {
        try {
            notificationService.notifyMessage(
                toUserId = recipientUserId,
                fromUserId = event.senderId,
                chatRoomId = event.roomId,
                messagePreview = event.messagePreview,
            )
            return
        } catch (e: Exception) {
            chatNotificationMetrics.fcmFailure.increment()
            log.warn(
                "Chat FCM first attempt failed recipient={} roomId={} messageId={}: {}",
                recipientUserId, event.roomId, event.messageId, e.message,
            )
        }

        try {
            Thread.sleep(RETRY_DELAY_MS)
            notificationService.notifyMessage(
                toUserId = recipientUserId,
                fromUserId = event.senderId,
                chatRoomId = event.roomId,
                messagePreview = event.messagePreview,
            )
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            chatNotificationMetrics.fcmDropped.increment()
            log.warn("Chat FCM retry interrupted, dropping recipient={} messageId={}", recipientUserId, event.messageId)
        } catch (e: Exception) {
            chatNotificationMetrics.fcmDropped.increment()
            log.error(
                "Chat FCM retry failed, dropping recipient={} roomId={} messageId={}: {}",
                recipientUserId, event.roomId, event.messageId, e.message,
            )
        }
    }

    companion object {
        const val RETRY_DELAY_MS: Long = 500
    }
}
