// STOMP SUBSCRIBE 인가(비참여자 실시간 broadcast 차단) 통합 테스트
package com.goldpet.domain.chat.integration

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.chat.dto.ChatMessageRequest
import com.goldpet.domain.chat.entity.ChatRoom
import com.goldpet.domain.chat.entity.ChatRoomParticipant
import com.goldpet.domain.chat.entity.ChatRoomRole
import com.goldpet.domain.chat.entity.ChatRoomType
import com.goldpet.domain.chat.entity.MessageType
import com.goldpet.domain.chat.repository.ChatMessageRepository
import com.goldpet.domain.chat.repository.ChatRoomParticipantRepository
import com.goldpet.domain.chat.repository.ChatRoomRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.Transport
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 리뷰 HIGH — STOMP SUBSCRIBE 무인가로 인한 실시간 채팅 IDOR 회귀 테스트.
 *
 * `StompAuthChannelInterceptor` 는 CONNECT 시 JWT 를 검증해 인증은 강제하지만, 인증된 "어떤"
 * 사용자든 임의 roomId 의 `/topic/chat/{roomId}` 를 SUBSCRIBE 해 실시간 broadcast 를 전량
 * 수신할 수 있었다(REST `getChatHistory` 픽스는 이력 조회만 막고 실시간 스트림은 무방비).
 *
 * 시나리오: GROUP 방에 userA 만 참여자로 등록. userB 는 유효 계정(CONNECT 는 성공)이지만
 * 방 참여자가 아니다. userA·userB 모두 방 토픽을 구독한 뒤 userA 가 실제 메시지를 송신하면,
 * 참여자(userA)는 수신해야 하고 비참여자(userB)는 수신하면 안 된다.
 *
 * 픽스 전에는 userB 도 수신해 이 테스트가 실패한다(red) — SUBSCRIBE 인가 추가 후 통과(green).
 *
 * ## 라운드2 재검증 HIGH — deny-by-default (allowlist 우회) 실제 브로커 레벨 검증
 * 위 시나리오는 리터럴 destination 만 다룬다. 실제 취약점은 브로커(`SimpleBrokerMessageHandler`)
 * 가 기본 `AntPathMatcher` 로 **클라이언트가 보낸 SUBSCRIBE destination 자체를 패턴으로 등록**
 * 한다는 데 있다 — 1차 구현의 정규식이 `/topic/chat/` 뒤에 별표 하나, `/topic/` 뒤에 별표
 * 두 개 같은 와일드카드에 매치되지 않으면 검사 없이 통과시켰고, 그 결과 이 패턴들이 그대로 브로커에 등록돼 모든
 * `/topic/chat/{n}` broadcast 를 매칭시켜 비참여자에게 전달했다. 아래
 * `wildcard subscribe destinations do not leak broadcasts to a non-participant` 가 실제
 * SimpleBrokerMessageHandler 를 통해 이 우회를 재현·검증한다(단위 테스트로는 재현 불가 —
 * AntPathMatcher 기반 브로커 라우팅은 실제 브로커 빈이 있어야 관찰 가능).
 *
 * ## leftAt (사용자 결정: 나가면 접근 불가)
 * `a participant who left the room cannot subscribe and receive broadcasts` 가 실제 DB에
 * leftAt≠null row 를 심어 `findByChatRoomIdAndUserIdAndLeftAtIsNull` 파생 쿼리가 진짜로
 * 필터링하는지(오타로 인한 무필터 위험까지) 검증한다.
 */
@Tag("integration")
class StompSubscribeAuthorizationIT : IntegrationTestBase() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var chatRoomRepository: ChatRoomRepository
    @Autowired private lateinit var chatRoomParticipantRepository: ChatRoomParticipantRepository
    @Autowired private lateinit var chatMessageRepository: ChatMessageRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    private val uid: String = UUID.randomUUID().toString().replace("-", "").take(10)

    private lateinit var userA: User
    private lateinit var userB: User
    private lateinit var chatRoom: ChatRoom

    @BeforeEach
    fun setUp() {
        userA = userRepository.save(makeUser("wssub_a_$uid"))
        userB = userRepository.save(makeUser("wssub_b_$uid")) // 유효 계정이지만 방 미참여
        chatRoom = chatRoomRepository.save(
            ChatRoom(roomType = ChatRoomType.GROUP, title = "sub-$uid", ownerUser = userA)
        )
        chatRoomParticipantRepository.save(
            ChatRoomParticipant(chatRoom = chatRoom, user = userA, role = ChatRoomRole.OWNER)
        )
        // userB 는 의도적으로 참여자로 등록하지 않는다 (공격자 시뮬레이션)
    }

    @AfterEach
    fun tearDown() {
        runCatching { chatMessageRepository.deleteByChatRoomId(chatRoom.id) }
        runCatching {
            chatRoomParticipantRepository.findAllByChatRoomId(chatRoom.id)
                .forEach { chatRoomParticipantRepository.delete(it) }
        }
        runCatching { chatRoomRepository.deleteById(chatRoom.id) }
        runCatching { userRepository.delete(userA) }
        runCatching { userRepository.delete(userB) }
    }

    @Test
    fun `non-participant subscriber does not receive room broadcasts while participant does`() {
        val wsUrl = "http://localhost:$port/ws"
        val transports: List<Transport> = listOf(WebSocketTransport(StandardWebSocketClient()))
        val stompClient = WebSocketStompClient(SockJsClient(transports)).apply {
            messageConverter = MappingJackson2MessageConverter()
        }

        val headersA = StompHeaders().apply { add("Authorization", "Bearer ${tokenFor(userA)}") }
        val headersB = StompHeaders().apply { add("Authorization", "Bearer ${tokenFor(userB)}") }

        val sessionA = stompClient.connectAsync(
            wsUrl, WebSocketHttpHeaders(), headersA, object : StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS)
        val sessionB = stompClient.connectAsync(
            wsUrl, WebSocketHttpHeaders(), headersB, object : StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS)

        val participantLatch = CountDownLatch(1)
        val nonParticipantReceived = CountDownLatch(1) // countDown 되면 안 됨(=유출)

        // userA(참여자) 구독 — 정상 수신 기대
        sessionA.subscribe("/topic/chat/${chatRoom.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                participantLatch.countDown()
            }
        })

        // userB(비참여자) 구독 — 수신되면 안 됨
        sessionB.subscribe("/topic/chat/${chatRoom.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                nonParticipantReceived.countDown()
            }
        })

        // 구독이 브로커에 등록될 시간 확보
        Thread.sleep(200)

        try {
            // userA 가 실제 메시지 송신 (본인은 참여자이므로 saveChatMessage 통과)
            sessionA.send(
                "/app/chat.sendMessage",
                ChatMessageRequest(
                    roomId = chatRoom.id,
                    senderId = userA.id,
                    messageType = MessageType.TEXT,
                    textContent = "participant-only-broadcast"
                )
            )

            assertThat(participantLatch.await(1_500, TimeUnit.MILLISECONDS))
                .withFailMessage("참여자(userA)가 자신이 구독한 방 broadcast 를 수신하지 못함")
                .isTrue()

            // 비참여자 수신 여부를 확인하기 위해 약간의 유예 시간을 둔다
            val leaked = nonParticipantReceived.await(500, TimeUnit.MILLISECONDS)
            assertThat(leaked)
                .withFailMessage("비참여자(userB)가 미참여 방의 실시간 broadcast 를 수신함 — SUBSCRIBE 인가 누락")
                .isFalse()
        } finally {
            runCatching { sessionA.disconnect() }
            runCatching { sessionB.disconnect() }
            stompClient.stop()
        }
    }

    /**
     * 라운드2 재검증 HIGH — 실제 브로커(AntPathMatcher 기반 SimpleBrokerMessageHandler)를 통해
     * 와일드카드 SUBSCRIBE 우회가 막혔는지 검증한다. 단위 테스트는 인터셉터 로직만 보므로 이
     * "구독 destination 자체가 브로커의 라우팅 패턴이 된다"는 실제 우회 메커니즘은 재현할 수
     * 없다 — 반드시 실제 브로커 빈을 띄운 통합 테스트로 확인해야 한다.
     */
    @Test
    fun `wildcard subscribe destinations do not leak broadcasts to a non-participant`() {
        val wildcardDestinations = listOf(
            "/topic/chat/*",
            "/topic/**",
            "/topic/chat/${chatRoom.id}*",
        )

        wildcardDestinations.forEach { destination -> assertWildcardDoesNotLeak(destination) }
    }

    private fun assertWildcardDoesNotLeak(destination: String) {
        val wsUrl = "http://localhost:$port/ws"
        val transports: List<Transport> = listOf(WebSocketTransport(StandardWebSocketClient()))
        val stompClient = WebSocketStompClient(SockJsClient(transports)).apply {
            messageConverter = MappingJackson2MessageConverter()
        }

        val headersA = StompHeaders().apply { add("Authorization", "Bearer ${tokenFor(userA)}") }
        val headersB = StompHeaders().apply { add("Authorization", "Bearer ${tokenFor(userB)}") }

        val sessionA = stompClient.connectAsync(
            wsUrl, WebSocketHttpHeaders(), headersA, object : StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS)
        val sessionB = stompClient.connectAsync(
            wsUrl, WebSocketHttpHeaders(), headersB, object : StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS)

        val participantLatch = CountDownLatch(1)
        val leakLatch = CountDownLatch(1)

        sessionA.subscribe("/topic/chat/${chatRoom.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) { participantLatch.countDown() }
        })
        // userB 가 리터럴 대신 와일드카드 destination 으로 구독 시도 (deny-by-default 우회 시도)
        sessionB.subscribe(destination, object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) { leakLatch.countDown() }
        })

        Thread.sleep(200)

        try {
            sessionA.send(
                "/app/chat.sendMessage",
                ChatMessageRequest(
                    roomId = chatRoom.id,
                    senderId = userA.id,
                    messageType = MessageType.TEXT,
                    textContent = "wildcard-bypass-probe"
                )
            )

            assertThat(participantLatch.await(1_500, TimeUnit.MILLISECONDS))
                .withFailMessage("참여자(userA)가 broadcast 를 수신하지 못함 — 테스트 셋업 문제 (destination=$destination)")
                .isTrue()

            val leaked = leakLatch.await(500, TimeUnit.MILLISECONDS)
            assertThat(leaked)
                .withFailMessage("와일드카드 구독($destination) 이 broadcast 를 수신함 — SUBSCRIBE deny-by-default 누락")
                .isFalse()
        } finally {
            runCatching { sessionA.disconnect() }
            runCatching { sessionB.disconnect() }
            stompClient.stop()
        }
    }

    /**
     * 사용자 결정(leftAt) — 나간 참여자는 실시간 스트림도 접근 불가해야 한다. 실제 DB에
     * leftAt≠null row 를 심어 `findByChatRoomIdAndUserIdAndLeftAtIsNull` 파생 쿼리가 그 row를
     * 진짜로 걸러내는지(메서드명 오타 등 정의 오류까지) 검증한다 — mock 기반 단위 테스트로는
     * derived query 자체의 정확성을 확인할 수 없다.
     */
    @Test
    fun `a participant who left the room cannot subscribe and receive broadcasts`() {
        val leftParticipant = chatRoomParticipantRepository.save(
            ChatRoomParticipant(chatRoom = chatRoom, user = userB, role = ChatRoomRole.MEMBER)
        )
        leftParticipant.leftAt = java.time.LocalDateTime.now()
        chatRoomParticipantRepository.save(leftParticipant)

        val wsUrl = "http://localhost:$port/ws"
        val transports: List<Transport> = listOf(WebSocketTransport(StandardWebSocketClient()))
        val stompClient = WebSocketStompClient(SockJsClient(transports)).apply {
            messageConverter = MappingJackson2MessageConverter()
        }

        val headersA = StompHeaders().apply { add("Authorization", "Bearer ${tokenFor(userA)}") }
        val headersB = StompHeaders().apply { add("Authorization", "Bearer ${tokenFor(userB)}") }

        val sessionA = stompClient.connectAsync(
            wsUrl, WebSocketHttpHeaders(), headersA, object : StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS)
        val sessionB = stompClient.connectAsync(
            wsUrl, WebSocketHttpHeaders(), headersB, object : StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS)

        val participantLatch = CountDownLatch(1)
        val leftUserReceived = CountDownLatch(1)

        sessionA.subscribe("/topic/chat/${chatRoom.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) { participantLatch.countDown() }
        })
        sessionB.subscribe("/topic/chat/${chatRoom.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) { leftUserReceived.countDown() }
        })

        Thread.sleep(200)

        try {
            sessionA.send(
                "/app/chat.sendMessage",
                ChatMessageRequest(
                    roomId = chatRoom.id,
                    senderId = userA.id,
                    messageType = MessageType.TEXT,
                    textContent = "left-user-probe"
                )
            )

            assertThat(participantLatch.await(1_500, TimeUnit.MILLISECONDS))
                .withFailMessage("참여자(userA)가 broadcast 를 수신하지 못함 — 테스트 셋업 문제")
                .isTrue()

            val leaked = leftUserReceived.await(500, TimeUnit.MILLISECONDS)
            assertThat(leaked)
                .withFailMessage("나간 참여자(userB, leftAt≠null)가 방 broadcast 를 수신함 — leftAt 필터 누락")
                .isFalse()
        } finally {
            runCatching { sessionA.disconnect() }
            runCatching { sessionB.disconnect() }
            stompClient.stop()
        }
    }

    private fun tokenFor(user: User): String =
        jwtTokenProvider.generateToken(
            UsernamePasswordAuthenticationToken(UserPrincipal(user, null), null, emptyList())
        )

    private fun makeUser(oauthId: String) = User(
        id = 0,
        email = "$oauthId@goldpet.com",
        oauthProvider = "LOCAL",
        oauthId = oauthId,
        username = oauthId,
        password = passwordEncoder.encode("pass"),
        nickname = oauthId,
        name = oauthId,
        birthDate = null,
        phoneNumber = null,
        gender = null,
        birthYear = null,
        mainLocationText = null,
        mainLocationGeom = null,
        profileImageUrl = null
    )
}
