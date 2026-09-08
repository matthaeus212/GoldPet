// StompAuthChannelInterceptor 의 CONNECT 인증/SUBSCRIBE 인가 단위 테스트
package com.goldpet.config

import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.CustomUserDetailsService
import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.chat.entity.ChatRoom
import com.goldpet.domain.chat.entity.ChatRoomParticipant
import com.goldpet.domain.chat.entity.ChatRoomRole
import com.goldpet.domain.chat.entity.ChatRoomType
import com.goldpet.domain.chat.repository.ChatRoomParticipantRepository
import com.goldpet.domain.user.entity.User
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageDeliveryException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.time.LocalDateTime
import java.util.Optional

/**
 * 독립 리뷰 LOW — StompAuthChannelInterceptor 단위 테스트 부재 보완.
 *
 * CONNECT 인증(토큰 없음/무효/type≠USER/정상) 4케이스, SUBSCRIBE 인가(비참여자 거부/참여자
 * 허용/leftAt 필터/본인 user-chats topic 허용/타인 topic 거부), 그리고 라운드2 재검증 HIGH —
 * deny-by-default 전환으로 막힌 와일드카드 allowlist 우회(`/topic/chat/` 뒤에 별표 하나,
 * `/topic/` 뒤에 별표 두 개, `/topic/chat/5` 뒤에 별표 하나, `/queue` 하위 전부)를 실증한다.
 */
class StompAuthChannelInterceptorTest {

    private lateinit var tokenProvider: JwtTokenProvider
    private lateinit var userDetailsService: CustomUserDetailsService
    private lateinit var chatRoomParticipantRepository: ChatRoomParticipantRepository
    private lateinit var interceptor: StompAuthChannelInterceptor
    private lateinit var channel: MessageChannel

    private lateinit var testUser: User
    private lateinit var testChatRoom: ChatRoom

    @BeforeEach
    fun setUp() {
        tokenProvider = mock()
        userDetailsService = mock()
        chatRoomParticipantRepository = mock()
        interceptor = StompAuthChannelInterceptor(tokenProvider, userDetailsService, chatRoomParticipantRepository)
        channel = mock()

        testUser = User(
            id = 1L,
            email = "test@example.com",
            oauthProvider = "GOOGLE",
            oauthId = "google123",
            username = null,
            password = null,
            nickname = "TestUser",
            name = null,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        testChatRoom = ChatRoom(id = 5L, roomType = ChatRoomType.GROUP, title = "Test Room").apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
    }

    // ── CONNECT ──────────────────────────────────────────────────────────

    private fun connectMessage(authHeader: String?): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(StompCommand.CONNECT)
        if (authHeader != null) accessor.addNativeHeader("Authorization", authHeader)
        accessor.setLeaveMutable(true)
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    @Test
    fun `CONNECT without Authorization header is rejected`() {
        assertThrows<MessageDeliveryException> {
            interceptor.preSend(connectMessage(null), channel)
        }
    }

    @Test
    fun `CONNECT with invalid token is rejected`() {
        whenever(tokenProvider.validateToken("bad-token")).thenReturn(false)

        assertThrows<MessageDeliveryException> {
            interceptor.preSend(connectMessage("Bearer bad-token"), channel)
        }
    }

    @Test
    fun `CONNECT with non-USER token type is rejected`() {
        whenever(tokenProvider.validateToken("admin-token")).thenReturn(true)
        whenever(tokenProvider.getUserTypeFromJWT("admin-token")).thenReturn("ADMIN")

        assertThrows<MessageDeliveryException> {
            interceptor.preSend(connectMessage("Bearer admin-token"), channel)
        }
    }

    @Test
    fun `CONNECT with valid USER token sets authenticated principal`() {
        whenever(tokenProvider.validateToken("good-token")).thenReturn(true)
        whenever(tokenProvider.getUserTypeFromJWT("good-token")).thenReturn("USER")
        whenever(tokenProvider.getUserIdFromJWT("good-token")).thenReturn("1")
        whenever(userDetailsService.loadUserById(1L)).thenReturn(UserPrincipal.create(testUser))

        val message = connectMessage("Bearer good-token")
        interceptor.preSend(message, channel)

        val accessor = StompHeaderAccessor.wrap(message)
        assertNotNull(accessor.user)
        val principal = (accessor.user as? UsernamePasswordAuthenticationToken)?.principal as? UserPrincipal
        assertNotNull(principal)
        org.junit.jupiter.api.Assertions.assertEquals(1L, principal!!.id)
    }

    // ── SUBSCRIBE (리뷰 HIGH) ────────────────────────────────────────────

    private fun subscribeMessage(destination: String, user: java.security.Principal?): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        accessor.destination = destination
        if (user != null) accessor.user = user
        accessor.setLeaveMutable(true)
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    private fun authPrincipal(user: User) =
        UsernamePasswordAuthenticationToken(UserPrincipal.create(user), null, emptyList())

    @Test
    fun `SUBSCRIBE to a room topic by a non-participant is rejected`() {
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(5L, 1L))
            .thenReturn(Optional.empty())

        assertThrows<MessageDeliveryException> {
            interceptor.preSend(subscribeMessage("/topic/chat/5", authPrincipal(testUser)), channel)
        }
    }

    @Test
    fun `SUBSCRIBE to a room topic by a participant is allowed`() {
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(5L, 1L))
            .thenReturn(Optional.of(ChatRoomParticipant(id = 1L, chatRoom = testChatRoom, user = testUser, role = ChatRoomRole.MEMBER)))

        // 예외 없이 통과해야 한다
        interceptor.preSend(subscribeMessage("/topic/chat/5", authPrincipal(testUser)), channel)
    }

    @Test
    fun `SUBSCRIBE to a room typing subtopic is also authorized`() {
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(5L, 1L))
            .thenReturn(Optional.empty())

        assertThrows<MessageDeliveryException> {
            interceptor.preSend(subscribeMessage("/topic/chat/5/typing", authPrincipal(testUser)), channel)
        }
    }

    @Test
    fun `SUBSCRIBE to a room read-receipt subtopic is also authorized`() {
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(5L, 1L))
            .thenReturn(Optional.of(ChatRoomParticipant(id = 1L, chatRoom = testChatRoom, user = testUser, role = ChatRoomRole.MEMBER)))

        interceptor.preSend(subscribeMessage("/topic/chat/5/read", authPrincipal(testUser)), channel)
    }

    @Test
    fun `SUBSCRIBE without an authenticated principal is rejected`() {
        assertThrows<MessageDeliveryException> {
            interceptor.preSend(subscribeMessage("/topic/chat/5", null), channel)
        }
    }

    @Test
    fun `SUBSCRIBE by a participant who left the room is rejected even though a stale row exists`() {
        // 라운드2 리뷰 MEDIUM(leftAt): old(비필터) 메서드는 일부러 present 로 스텁 — 인터셉터가
        // 실수로 그 메서드를 쓰면 이 테스트가 통과(구독 허용)해버려 필터 누락을 잡아낸다.
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserId(5L, 1L))
            .thenReturn(Optional.of(
                ChatRoomParticipant(id = 1L, chatRoom = testChatRoom, user = testUser, role = ChatRoomRole.MEMBER, leftAt = LocalDateTime.now())
            ))
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(5L, 1L))
            .thenReturn(Optional.empty())

        assertThrows<MessageDeliveryException> {
            interceptor.preSend(subscribeMessage("/topic/chat/5", authPrincipal(testUser)), channel)
        }
    }

    // ── SUBSCRIBE 본인 채팅목록 topic (`/topic/user/{id}/chats`) ────────────

    @Test
    fun `SUBSCRIBE to own user-chats topic is allowed without a repository call`() {
        // 본인 topic 은 소유권 비교만으로 판정 — DB 조회 불필요.
        interceptor.preSend(subscribeMessage("/topic/user/1/chats", authPrincipal(testUser)), channel)
        verifyNoInteractions(chatRoomParticipantRepository)
    }

    @Test
    fun `SUBSCRIBE to another user's chats topic is denied (IDOR)`() {
        assertThrows<MessageDeliveryException> {
            interceptor.preSend(subscribeMessage("/topic/user/999/chats", authPrincipal(testUser)), channel)
        }
    }

    // ── SUBSCRIBE deny-by-default (라운드2 재검증 HIGH — allowlist 우회) ────
    // 1차 구현은 정규식 매치 실패 시 `?: return` 으로 검사 없이 통과시켰다. 브로커가 기본
    // AntPathMatcher 로 SUBSCRIBE destination 자체를 패턴으로 등록하므로, 이 와일드카드들이
    // 뚫리면 비참여자가 모든 `/topic/chat/{n}` broadcast 를 수신할 수 있었다.

    @Test
    fun `SUBSCRIBE to a single-segment wildcard room destination is denied`() {
        assertThrows<MessageDeliveryException> {
            interceptor.preSend(subscribeMessage("/topic/chat/*", authPrincipal(testUser)), channel)
        }
        verifyNoInteractions(chatRoomParticipantRepository)
    }

    @Test
    fun `SUBSCRIBE to a recursive wildcard destination is denied`() {
        assertThrows<MessageDeliveryException> {
            interceptor.preSend(subscribeMessage("/topic/**", authPrincipal(testUser)), channel)
        }
        verifyNoInteractions(chatRoomParticipantRepository)
    }

    @Test
    fun `SUBSCRIBE to a prefix-wildcard room destination is denied`() {
        assertThrows<MessageDeliveryException> {
            interceptor.preSend(subscribeMessage("/topic/chat/5*", authPrincipal(testUser)), channel)
        }
        verifyNoInteractions(chatRoomParticipantRepository)
    }

    @Test
    fun `SUBSCRIBE to a destination outside the allowlist under the queue prefix is denied`() {
        // 정상 클라이언트는 `/queue` 를 구독하지 않는다(grep 확인) — allowlist 밖이라 보수적으로 거부.
        assertThrows<MessageDeliveryException> {
            interceptor.preSend(subscribeMessage("/queue/chat/5", authPrincipal(testUser)), channel)
        }
        verifyNoInteractions(chatRoomParticipantRepository)
    }

    @Test
    fun `SUBSCRIBE to an unrelated literal destination is denied by default`() {
        assertThrows<MessageDeliveryException> {
            interceptor.preSend(subscribeMessage("/topic/something-else", authPrincipal(testUser)), channel)
        }
        verifyNoInteractions(chatRoomParticipantRepository)
    }
}
