// STOMP CONNECT/SUBSCRIBE 프레임을 인가하는 인바운드 채널 인터셉터
package com.goldpet.config

import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.CustomUserDetailsService
import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.chat.repository.ChatRoomParticipantRepository
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageDeliveryException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

/**
 * EXT-CDX-002 (W1a, critical) — WebSocket STOMP 무인증/무인가 차단.
 *
 * REST 는 [com.goldpet.config.jwt.JwtAuthenticationFilter] 가 `Authorization: Bearer` 헤더의
 * JWT 를 검증하지만, `/ws` STOMP 채널에는 인증이 전혀 없어 payload 의 senderId 를 그대로 신뢰했다.
 * 프론트는 이미 STOMP CONNECT 헤더로 Bearer 토큰을 전송하므로(chatWebSocket.ts), 백엔드에서
 * CONNECT 시 동일 방식으로 토큰을 검증하고 [StompHeaderAccessor.setUser] 로 Principal 을 주입한다.
 * 이후 `@MessageMapping` 핸들러는 이 Principal 로 실제 사용자 id 를 얻는다.
 *
 * 검증 실패 시 [MessageDeliveryException] 을 던져 CONNECT 를 거부한다(연결 미수립).
 *
 * ## SUBSCRIBE 인가 (독립 리뷰 HIGH — 실시간 채팅 IDOR)
 * CONNECT 인증만으로는 부족하다: 인증된 사용자라면 누구든 임의 roomId 의 `/topic/chat/{roomId}`
 * (및 `/typing`, `/read` 등 하위 서브토픽)를 구독해 미참여 방의 실시간 broadcast 를 전량 수신할
 * 수 있었다(REST `ChatService.getChatHistory` 픽스는 이력 조회만 막고 실시간 스트림은 무방비).
 * SUBSCRIBE 프레임의 destination 에서 roomId 를 파싱해 참여자 여부를 검증한다. Spring 은 CONNECT
 * 에서 설정한 Principal 을 동일 세션의 후속 프레임(SUBSCRIBE 포함)에도 자동 전파하므로 별도
 * 재인증 없이 `accessor.user` 로 조회 가능하다.
 *
 * ## deny-by-default 전환 (라운드2 재검증 HIGH — allowlist 우회)
 * 1차 구현은 destination 이 `CHAT_ROOM_TOPIC` 정규식에 매치되지 않으면 `?: return` 으로 **검사
 * 없이 통과**시켰다. 그런데 STOMP 브로커(`SimpleBrokerMessageHandler`)는 기본 `AntPathMatcher`
 * 를 써서 **클라이언트가 보낸 SUBSCRIBE destination 자체를 패턴으로 등록**하고, 이후 도착하는
 * 메시지의 실제 destination 을 그 패턴에 매칭시켜 전달한다. 즉 `/topic/chat/` 뒤에 별표 하나,
 * `/topic/` 뒤에 별표 두 개(재귀 와일드카드), `/topic/chat/5` 뒤에 별표 하나 같은 와일드카드
 * 구독은 우리 정규식(숫자만 허용)에 매치되지 않아 검사를
 * 건너뛰고 그대로 브로커에 등록되고, 브로커가 모든 `/topic/chat/{n}` broadcast 를 그 패턴에
 * 매칭시켜 전송한다 — 참여자 검증이 완전히 우회된다.
 *
 * 전환: **deny-by-default**. 모든 SUBSCRIBE 는 (1) 인증된 Principal 필수, (2) 정확한 literal
 * allowlist 에 `matchEntire` 로 온전히 일치할 때만 통과, 그 외 전부 [MessageDeliveryException]
 * 으로 거부한다. allowlist 정규식 자체가 roomId/userId 자리에 `\d+`(숫자)만 허용하는 엄격한
 * `matchEntire` 매칭이라 `*`/`?`/`**`/`,` 같은 AntPathMatcher 메타문자는 애초에 매치되지 않고
 * 자동으로 최종 거부 분기에 떨어진다 — 별도 메타문자 필터가 불필요하다.
 *
 * `/topic`·`/queue` 이외 prefix: 브로커가 이 두 prefix 만 관리하도록 구성돼 있고
 * (`WebSocketConfig.configureMessageBroker`), 실제 클라이언트(frontend/src/services/websocket/
 * chatWebSocket.ts, useChatWebSocket.ts, useChatListWebSocket.ts 전수 grep 확인 — app/ Flutter
 * 는 STOMP 클라이언트 자체가 없음)가 구독하는 destination 은 `/topic/chat/{roomId}`,
 * `/topic/chat/{roomId}/typing`, `/topic/chat/{roomId}/read`, `/topic/user/{userId}/chats`
 * 4종뿐이며 `/queue` 하위 구독(와일드카드 포함)은 전무하다. 따라서 allowlist 에 없는 나머지는
 * `/queue` 하위 전부를 포함해 보수적으로 전부 거부해도 정상 클라이언트를 깨지 않는다.
 *
 * ## leftAt 필터 (사용자 결정: 나가면 접근 불가)
 * `findByChatRoomIdAndUserIdAndLeftAtIsNull` 사용 — 나간 참여자(leftAt≠null)는 SUBSCRIBE 로
 * 방을 재열람할 수 없다.
 */
@Component
class StompAuthChannelInterceptor(
    private val tokenProvider: JwtTokenProvider,
    private val customUserDetailsService: CustomUserDetailsService,
    private val chatRoomParticipantRepository: ChatRoomParticipantRepository,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> handleConnect(accessor)
            StompCommand.SUBSCRIBE -> handleSubscribe(accessor)
            else -> Unit
        }
        return message
    }

    private fun handleConnect(accessor: StompHeaderAccessor) {
        val token = resolveToken(accessor)
            ?: throw MessageDeliveryException("WebSocket 인증 실패: 토큰이 없습니다")
        if (!tokenProvider.validateToken(token)) {
            throw MessageDeliveryException("WebSocket 인증 실패: 유효하지 않은 토큰입니다")
        }
        // 채팅 WebSocket 은 일반 사용자 전용. ADMIN/REFRESH 등 다른 타입은 거부.
        if (tokenProvider.getUserTypeFromJWT(token) != "USER") {
            throw MessageDeliveryException("WebSocket 인증 실패: 지원하지 않는 토큰 타입입니다")
        }
        val userId = tokenProvider.getUserIdFromJWT(token).toLong()
        val userDetails = customUserDetailsService.loadUserById(userId)
        accessor.user = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
    }

    /**
     * deny-by-default: 인증 먼저 강제 → 정확 literal allowlist 매치만 통과 → 그 외(와일드카드
     * 포함) 전부 거부. `?: return` 같은 "매치 실패=통과" 경로가 존재하지 않는다.
     */
    private fun handleSubscribe(accessor: StompHeaderAccessor) {
        val destination = accessor.destination
            ?: throw MessageDeliveryException("WebSocket 구독 거부: destination 이 없습니다")
        val userId = resolveUserId(accessor)
            ?: throw MessageDeliveryException("WebSocket 구독 인가 실패: 인증되지 않았습니다")

        val chatRoomId = CHAT_ROOM_TOPIC.matchEntire(destination)?.groupValues?.get(1)?.toLongOrNull()
        if (chatRoomId != null) {
            // 리뷰 MEDIUM(leftAt): 나간 참여자는 구독 불가 — 접근 인가 검증이므로 필터 적용.
            chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(chatRoomId, userId)
                .orElseThrow { MessageDeliveryException("WebSocket 구독 인가 실패: 채팅방 참여자가 아닙니다") }
            return
        }

        val userChatsTargetId = USER_CHATS_TOPIC.matchEntire(destination)?.groupValues?.get(1)?.toLongOrNull()
        if (userChatsTargetId != null) {
            if (userChatsTargetId != userId) {
                throw MessageDeliveryException("WebSocket 구독 인가 실패: 본인 topic 만 구독 가능합니다")
            }
            return
        }

        // allowlist 밖 — 리터럴이든 와일드카드든 전부 거부(deny-by-default).
        throw MessageDeliveryException("WebSocket 구독 거부: 허용되지 않는 destination 입니다")
    }

    private fun resolveUserId(accessor: StompHeaderAccessor): Long? {
        val userPrincipal = (accessor.user as? Authentication)?.principal as? UserPrincipal
        return userPrincipal?.id
    }

    private fun resolveToken(accessor: StompHeaderAccessor): String? {
        val header = accessor.getFirstNativeHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ")) header.substring(7) else null
    }

    companion object {
        // roomId 자리가 `\d+`(숫자)만 허용 — `*`/`**`/`?`/`,` 등 AntPathMatcher 메타문자는
        // 매치 자체가 불가해 자동으로 handleSubscribe 최종 거부 분기로 떨어진다.
        private val CHAT_ROOM_TOPIC = Regex("""^/topic/chat/(\d+)(?:/(?:typing|read))?$""")
        // 채팅 목록 알림 topic — 캡처한 id 가 SUBSCRIBE 요청자 본인이어야 통과.
        private val USER_CHATS_TOPIC = Regex("""^/topic/user/(\d+)/chats$""")
    }
}
