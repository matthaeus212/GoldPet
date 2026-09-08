package com.goldpet.domain.chat.event

import com.goldpet.domain.chat.metrics.ChatNotificationMetrics
import com.goldpet.domain.metrics.ChatLatencyMetrics
import com.goldpet.domain.notification.service.NotificationService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * ChatNotificationEventListener 단위 테스트.
 *
 * Spring 컨텍스트 없이 순수 Mockito 로 리스너 동작 검증.
 * - SimpleMeterRegistry 로 카운터 값을 직접 단언.
 * - ChatLatencyMetrics 는 실제 인스턴스 사용 (TimerSample 오버헤드 무시).
 * - 재시도 테스트는 Thread.sleep(RETRY_DELAY_MS=500ms) 를 포함 → 테스트당 약 500ms 추가 소요.
 */
class ChatNotificationEventListenerTest {

    @Mock
    private lateinit var notificationService: NotificationService

    private val meterRegistry = SimpleMeterRegistry()
    private lateinit var chatLatencyMetrics: ChatLatencyMetrics
    private lateinit var chatNotificationMetrics: ChatNotificationMetrics
    private lateinit var listener: ChatNotificationEventListener

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        chatLatencyMetrics = ChatLatencyMetrics(meterRegistry).apply { preregister() }
        chatNotificationMetrics = ChatNotificationMetrics(meterRegistry).apply { register() }
        listener = ChatNotificationEventListener(notificationService, chatLatencyMetrics, chatNotificationMetrics)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    private fun makeEvent(
        recipientUserIds: List<Long>,
        messageId: Long = 1L,
        roomId: Long = 10L,
        senderId: Long = 100L,
        clientMsgId: String? = null,
    ) = ChatMessageSavedEvent(
        messageId = messageId,
        roomId = roomId,
        senderId = senderId,
        recipientUserIds = recipientUserIds,
        messagePreview = "테스트 메시지",
        clientMsgId = clientMsgId,
    )

    // ──────────────────────────────────────────────────────────────────────────
    // 정상 흐름
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `recipient 2명이면 notifyMessage 정확히 2회 호출`() {
        // When
        listener.handleChatMessageSaved(makeEvent(listOf(200L, 300L)))

        // Then
        verify(notificationService, times(2)).notifyMessage(any(), any(), any(), any())
    }

    @Test
    fun `recipientUserIds 빈 리스트면 notifyMessage 호출 없음`() {
        // When
        listener.handleChatMessageSaved(makeEvent(emptyList()))

        // Then
        verify(notificationService, never()).notifyMessage(any(), any(), any(), any())
    }

    @Test
    fun `정상 흐름에서 fcmFailure 와 fcmDropped 는 0 유지`() {
        // When
        listener.handleChatMessageSaved(makeEvent(listOf(200L)))

        // Then
        assertEquals(0.0, chatNotificationMetrics.fcmFailure.count(), 0.001)
        assertEquals(0.0, chatNotificationMetrics.fcmDropped.count(), 0.001)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 재시도 정책
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `FCM 1차 실패 후 재시도 성공 — fcmFailure=1 fcmDropped=0`() {
        // Given: 첫 호출 실패, 두 번째 doNothing (Unit 반환 메서드는 thenReturn 불가)
        doThrow(RuntimeException("FCM 1차 실패")).doNothing()
            .whenever(notificationService).notifyMessage(any(), any(), any(), any())

        // When
        listener.handleChatMessageSaved(makeEvent(listOf(200L), messageId = 2L))

        // Then: 총 2회 호출 (최초 + 재시도)
        verify(notificationService, times(2)).notifyMessage(any(), any(), any(), any())
        assertEquals(1.0, chatNotificationMetrics.fcmFailure.count(), 0.001,
            "1차 실패 카운터 +1")
        assertEquals(0.0, chatNotificationMetrics.fcmDropped.count(), 0.001,
            "재시도 성공이므로 dropped 는 0")
    }

    @Test
    fun `FCM 1차 2차 모두 실패 — fcmFailure=1 fcmDropped=1 예외 전파 없음`() {
        // Given: 모든 호출 실패
        doThrow(RuntimeException("FCM 영구 실패"))
            .whenever(notificationService).notifyMessage(any(), any(), any(), any())

        // When — 예외가 리스너 바깥으로 전파되지 않아야 함
        listener.handleChatMessageSaved(makeEvent(listOf(200L), messageId = 3L))

        // Then
        verify(notificationService, times(2)).notifyMessage(any(), any(), any(), any())
        assertEquals(1.0, chatNotificationMetrics.fcmFailure.count(), 0.001)
        assertEquals(1.0, chatNotificationMetrics.fcmDropped.count(), 0.001)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 수신자 격리
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `수신자 A FCM 영구 실패해도 수신자 B 알림은 정상 전송`() {
        // Given: 200L 은 항상 실패, 300L 은 성공 (Unit 반환 메서드는 thenReturn 불가)
        whenever(notificationService.notifyMessage(eq(200L), any(), any(), any()))
            .thenThrow(RuntimeException("200L 1차 실패"))
            .thenThrow(RuntimeException("200L 재시도 실패"))
        doNothing()
            .whenever(notificationService).notifyMessage(eq(300L), any(), any(), any())

        // When
        listener.handleChatMessageSaved(makeEvent(listOf(200L, 300L), messageId = 4L))

        // Then: 300L 은 1회 정상 수신, 200L failure 카운팅 정확
        verify(notificationService, times(1)).notifyMessage(eq(300L), any(), any(), any())
        assertEquals(1.0, chatNotificationMetrics.fcmFailure.count(), 0.001)
        assertEquals(1.0, chatNotificationMetrics.fcmDropped.count(), 0.001)
    }
}
