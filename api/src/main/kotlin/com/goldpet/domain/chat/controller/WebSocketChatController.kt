package com.goldpet.domain.chat.controller

import com.goldpet.domain.chat.dto.ChatMessageRequest
import com.goldpet.domain.chat.service.ChatService
import com.goldpet.domain.metrics.ChatLatencyMetrics
import com.goldpet.config.openapi.OpenApiInternal
import com.goldpet.config.security.UserPrincipal
import org.springframework.messaging.MessageDeliveryException
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageSendingOperations
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import java.security.Principal

data class TypingEvent(
    val roomId: Long,
    val userId: Long,
    val isTyping: Boolean
)

@OpenApiInternal
@Controller
class WebSocketChatController(
    private val simpMessageSendingOperations: SimpMessageSendingOperations,
    private val chatService: ChatService,
    private val chatLatencyMetrics: ChatLatencyMetrics
) {

    @MessageMapping("/chat.sendMessage")
    fun sendMessage(@Payload chatMessageRequest: ChatMessageRequest, principal: Principal) {
        // EXT-CDX-002 (W1a): payload senderId 를 신뢰하지 않고 인증 Principal 의 userId 로 강제.
        // 구프론트가 payload 에 senderId 를 계속 보내도 무시(= 호환). 방 참여자 검증은
        // ChatService.saveChatMessage 가 이미 수행(NOT_PARTICIPANT).
        val senderId = resolveUserId(principal)
        val authorizedRequest = chatMessageRequest.copy(senderId = senderId)
        chatLatencyMetrics.recordServerLatency(ChatLatencyMetrics.Phase.PRE_ASYNC) {
            // ARCH-003: 이 핸들러도 REST 경로와 똑같은 조립을 손으로 하고 있었다(리포지토리 직접 주입).
            // ChatService.sendMessage 로 수렴 — 두 경로가 같은 규칙으로 조립된다.
            // 브로드캐스트 범위는 기존과 동일하게 /topic/chat/{roomId} 만 유지한다(동작 변경 없음).
            val sent = chatService.sendMessage(authorizedRequest)
            simpMessageSendingOperations.convertAndSend("/topic/chat/${authorizedRequest.roomId}", sent.response)
        }
    }

    private fun resolveUserId(principal: Principal): Long {
        val userPrincipal = (principal as? Authentication)?.principal as? UserPrincipal
        return userPrincipal?.id
            ?: throw MessageDeliveryException("WebSocket 사용자 식별 실패")
    }

    @MessageMapping("/chat.typing")
    fun typing(@Payload typingEvent: TypingEvent, principal: Principal) {
        // 독립 리뷰 MEDIUM: payload 의 userId 를 신뢰하지 않고 Principal 로 강제(타인 위조 방지).
        // SUBSCRIBE 인가(StompAuthChannelInterceptor)가 구독 측을 막지만, SEND 측(본 핸들러)도
        // 미참여자가 임의 방에 위조 이벤트를 발행하지 못하도록 별도로 참여자 검증한다.
        val userId = resolveUserId(principal)
        // 라운드2 리뷰 MEDIUM(leftAt): 나간 참여자는 typing 발행 불가 — 접근 인가 검증이므로 필터 적용.
        // ARCH-005: 리포지토리 직접 조회 → 서비스 계약(isActiveParticipant)으로 전환.
        if (!chatService.isActiveParticipant(typingEvent.roomId, userId)) {
            throw MessageDeliveryException("채팅방 참여자가 아닙니다")
        }
        val authorizedEvent = typingEvent.copy(userId = userId)
        // Broadcast typing status to all subscribers of the room
        simpMessageSendingOperations.convertAndSend(
            "/topic/chat/${typingEvent.roomId}/typing",
            authorizedEvent
        )
    }
}
