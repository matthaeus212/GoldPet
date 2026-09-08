package com.goldpet.domain.chat.event

/**
 * T-chat-latency-v2 Step 2 — chat 메시지 영속화 이후 `AFTER_COMMIT` phase 에서 발행되는 도메인 이벤트.
 *
 * ## 설계
 * - **Payload 는 스칼라만** — `ChatMessage` / `User` 등 엔티티 참조를 담지 않는다.
 *   async 리스너에서 Hibernate detached / lazy-init 예외를 구조적으로 차단.
 * - **recipientUserIds 는 publish 시점에 이미 계산된 active participant 목록** (sender 제외).
 *   리스너가 `ChatRoomParticipantRepository.findAll*` 을 재호출하는 중복 쿼리 제거 — DoD 충족.
 * - **clientMsgId** 는 로그 상관관계 추적용. 알림 본문에는 쓰이지 않음.
 *
 * ## Lifecycle
 * 1. `ChatService.saveChatMessage` 트랜잭션 COMMIT
 * 2. `ApplicationEventPublisher.publishEvent(ChatMessageSavedEvent(...))`
 * 3. `ChatNotificationEventListener` 가 `chatNotificationExecutor` 풀에서 수신 → FCM 발송
 */
data class ChatMessageSavedEvent(
    val messageId: Long,
    val roomId: Long,
    val senderId: Long,
    val recipientUserIds: List<Long>,
    val messagePreview: String,
    val clientMsgId: String? = null,
)
