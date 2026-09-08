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
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Tag
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
 * WebSocket STOMP 통합 테스트 — 1채팅방 2클라이언트 경쟁 시나리오 (Phase 1 이월).
 *
 * 시나리오:
 *   1. GROUP 채팅방 생성 + 2 유저 참여자 등록
 *   2. SockJS/STOMP 클라이언트 A·B 동시 연결 + B가 룸 토픽 구독
 *   3. A가 /app/chat.sendMessage 에 메시지 2건 연속 송신
 *   4. B가 /topic/chat/{roomId} 에서 1 초 내 2건 모두 수신 확인
 *   5. 수신 ID ASC 순서 보존 검증 (seq 순서 보존)
 *
 * EXT-CDX-002(W1a): STOMP CONNECT 는 이제 JWT 인증 필수. 각 클라이언트는 CONNECT 헤더에
 * `Authorization: Bearer <token>` 를 실어야 하며, 발신자 id 는 payload 가 아닌 Principal 로 결정된다.
 * Testcontainers 미사용 — deploy-local PostGIS 컨테이너(localhost:5433) 사용.
 */
@Tag("integration")
class WebSocketRaceIT : IntegrationTestBase() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var chatRoomRepository: ChatRoomRepository
    @Autowired private lateinit var chatRoomParticipantRepository: ChatRoomParticipantRepository
    @Autowired private lateinit var chatMessageRepository: ChatMessageRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider

    // PER_METHOD 라이프사이클: 반복마다 새 인스턴스 → uid 도 매번 새로 생성됨
    private val uid: String = UUID.randomUUID().toString().replace("-", "").take(10)

    private lateinit var userA: User
    private lateinit var userB: User
    private lateinit var chatRoom: ChatRoom

    @BeforeEach
    fun setUp() {
        userA = userRepository.save(makeUser("wsrace_a_$uid"))
        userB = userRepository.save(makeUser("wsrace_b_$uid"))
        chatRoom = chatRoomRepository.save(
            ChatRoom(roomType = ChatRoomType.GROUP, title = "race-$uid", ownerUser = userA)
        )
        chatRoomParticipantRepository.save(
            ChatRoomParticipant(chatRoom = chatRoom, user = userA, role = ChatRoomRole.OWNER)
        )
        chatRoomParticipantRepository.save(
            ChatRoomParticipant(chatRoom = chatRoom, user = userB, role = ChatRoomRole.MEMBER)
        )
    }

    @AfterEach
    fun tearDown() {
        // 메시지 → 참여자 → 채팅방 → 유저 순서로 FK 참조 역방향 삭제
        runCatching { chatMessageRepository.deleteByChatRoomId(chatRoom.id) }
        runCatching {
            chatRoomParticipantRepository.findAllByChatRoomId(chatRoom.id)
                .forEach { chatRoomParticipantRepository.delete(it) }
        }
        runCatching { chatRoomRepository.deleteById(chatRoom.id) }
        runCatching { userRepository.delete(userA) }
        runCatching { userRepository.delete(userB) }
    }

    /**
     * A·B 두 클라이언트 동시 접속 + B 구독 후, A 가 메시지를 송신하면
     * B 가 1초 내 수신하고 텍스트 내용이 정확함을 5회 반복 검증한다.
     *
     * seq 순서 보존 전략:
     *   A 가 msg-1 을 먼저 송신하고 수신 확인 후 msg-2 를 송신한다.
     *   수신 순서에서 msg-2 의 DB id 가 msg-1 보다 큼을 검증한다.
     *   (순차 latch 로 트랜잭션 커밋 완료 후 다음 메시지를 송신하므로
     *    concurrent commit 순서 역전 없이 id 단조 증가가 보장된다.)
     */
    @RepeatedTest(5)
    fun `two clients connect concurrently - messages delivered within 1s and seq id preserved`() {
        val wsUrl = "http://localhost:$port/ws"
        val transports: List<Transport> = listOf(WebSocketTransport(StandardWebSocketClient()))
        val stompClient = WebSocketStompClient(SockJsClient(transports)).apply {
            messageConverter = MappingJackson2MessageConverter()
        }

        // 2개의 메시지를 순차적으로 기다리기 위한 latch 2개
        val latch1 = CountDownLatch(1)
        val latch2 = CountDownLatch(1)
        val receivedIds = mutableListOf<Long>()
        val receivedTexts = mutableListOf<String>()

        // EXT-CDX-002(W1a): 각 CONNECT 에 JWT Bearer 토큰 첨부 (인터셉터가 Principal 주입)
        val headersA = StompHeaders().apply { add("Authorization", "Bearer ${tokenFor(userA)}") }
        val headersB = StompHeaders().apply { add("Authorization", "Bearer ${tokenFor(userB)}") }

        // 두 클라이언트 동시 연결
        val sessionA = stompClient.connectAsync(
            wsUrl, WebSocketHttpHeaders(), headersA, object : StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS)
        val sessionB = stompClient.connectAsync(
            wsUrl, WebSocketHttpHeaders(), headersB, object : StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS)

        // B 가 룸 토픽 구독
        sessionB.subscribe("/topic/chat/${chatRoom.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

            @Suppress("UNCHECKED_CAST")
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                val msg = payload as? Map<String, Any> ?: return
                synchronized(receivedIds) {
                    (msg["id"] as? Number)?.toLong()?.let { receivedIds.add(it) }
                    (msg["textContent"] as? String)?.let { receivedTexts.add(it) }
                }
                if (latch1.count > 0) latch1.countDown() else latch2.countDown()
            }
        })

        // 구독이 브로커에 등록될 시간 확보
        Thread.sleep(200)

        try {
            val t0 = System.currentTimeMillis()

            // A 가 msg-1 송신 → B 수신 확인 후 msg-2 송신 (순차 보장)
            sessionA.send(
                "/app/chat.sendMessage",
                ChatMessageRequest(
                    roomId = chatRoom.id,
                    senderId = userA.id,
                    messageType = MessageType.TEXT,
                    textContent = "race-msg-1"
                )
            )
            val msg1Received = latch1.await(1_000, TimeUnit.MILLISECONDS)
            assertThat(msg1Received)
                .withFailMessage("msg-1 이 1초 내 수신되지 않음")
                .isTrue()

            sessionA.send(
                "/app/chat.sendMessage",
                ChatMessageRequest(
                    roomId = chatRoom.id,
                    senderId = userA.id,
                    messageType = MessageType.TEXT,
                    textContent = "race-msg-2"
                )
            )
            val msg2Received = latch2.await(1_000, TimeUnit.MILLISECONDS)
            val elapsed = System.currentTimeMillis() - t0

            assertThat(msg2Received)
                .withFailMessage("msg-2 가 1초 내 수신되지 않음; elapsed=${elapsed}ms")
                .isTrue()
            assertThat(elapsed).isLessThan(2_000L) // 2건 합산 2s 이내
            assertThat(receivedTexts).containsExactly("race-msg-1", "race-msg-2")
            // seq 순서 보존: 순차 latch 덕분에 id 단조 증가 보장
            assertThat(receivedIds).hasSize(2)
            assertThat(receivedIds[1])
                .withFailMessage("seq 순서 불일치: ids=$receivedIds")
                .isGreaterThan(receivedIds[0])
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
