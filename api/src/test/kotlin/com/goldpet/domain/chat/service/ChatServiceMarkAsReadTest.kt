// 채팅 첫 읽음(oldLastReadAt == null) 처리 시 LocalDateTime.MIN이 아닌 epoch 기준으로 조회되는지 검증하는 회귀 테스트
package com.goldpet.domain.chat.service

import com.goldpet.domain.chat.entity.ChatRoom
import com.goldpet.domain.chat.entity.ChatRoomParticipant
import com.goldpet.domain.chat.entity.ChatRoomRole
import com.goldpet.domain.chat.entity.ChatRoomType
import com.goldpet.domain.chat.repository.ChatMessageRepository
import com.goldpet.domain.chat.repository.ChatRequestRepository
import com.goldpet.domain.chat.repository.ChatRoomParticipantRepository
import com.goldpet.domain.chat.repository.ChatRoomRepository
import com.goldpet.domain.friend.repository.MatchRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.notification.service.NotificationService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.Optional

/**
 * ARCH-003 이전에는 이 계산이 ChatRoomController 안에 있었고, 회귀 테스트도 컨트롤러를 겨눴다.
 * 로직이 ChatService.markAsReadAndCollectUpdates 로 이관됐으므로 테스트도 함께 옮긴다
 * (커버리지를 잃지 않는 것이 이 이관의 조건이다).
 */
class ChatServiceMarkAsReadTest {

    @Mock private lateinit var chatRoomRepository: ChatRoomRepository
    @Mock private lateinit var chatRoomParticipantRepository: ChatRoomParticipantRepository
    @Mock private lateinit var chatMessageRepository: ChatMessageRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var matchRepository: MatchRepository
    @Mock private lateinit var notificationService: NotificationService
    @Mock private lateinit var userBlockRepository: UserBlockRepository
    @Mock private lateinit var chatRequestRepository: ChatRequestRepository
    @Mock private lateinit var applicationEventPublisher: ApplicationEventPublisher
    @Mock private lateinit var chatMessageAssembler: ChatMessageAssembler

    private lateinit var chatService: ChatService
    private lateinit var participant: ChatRoomParticipant

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        chatService = ChatService(
            chatRoomRepository,
            chatRoomParticipantRepository,
            chatMessageRepository,
            userRepository,
            matchRepository,
            notificationService,
            userBlockRepository,
            chatRequestRepository,
            applicationEventPublisher,
            chatMessageAssembler,
            asyncNotificationEnabled = false,
        )

        val user = User(
            id = 1L,
            email = "test@example.com",
            oauthProvider = "LOCAL",
            oauthId = "LOCAL_testuser",
            username = "testuser",
            password = "password",
            nickname = "테스트유저",
            name = "테스트유저",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )
        val room = ChatRoom(id = 1L, roomType = ChatRoomType.DIRECT)
        participant = ChatRoomParticipant(id = 1L, chatRoom = room, user = user, role = ChatRoomRole.MEMBER)
    }

    // dd85ec59: 첫 읽음(oldLastReadAt == null) 처리 시 LocalDateTime.MIN 을 그대로 PG 쿼리에
    // 넘기면 pgjdbc 변환 오버플로로 "timestamp out of range" 500 이 났다.
    // epoch(1970-01-01) 대체 수정이 유지되는지 고정한다.
    @Test
    fun `queries since epoch not LocalDateTime MIN on first read`() {
        participant.lastReadAt = null
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(1L, 1L))
            .thenReturn(Optional.of(participant))
        whenever(chatMessageRepository.findVisibleByChatRoomIdAndCreatedAtAfter(eq(1L), org.mockito.kotlin.any()))
            .thenReturn(emptyList())

        val updates = chatService.markAsReadAndCollectUpdates(1L, 1L)

        assertTrue(updates.isEmpty())
        val captor = argumentCaptor<LocalDateTime>()
        verify(chatMessageRepository).findVisibleByChatRoomIdAndCreatedAtAfter(eq(1L), captor.capture())
        assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0), captor.firstValue)
        assertNotEquals(LocalDateTime.MIN, captor.firstValue)
    }

    @Test
    fun `queries since oldLastReadAt when not first read`() {
        val previousRead = LocalDateTime.of(2026, 1, 1, 12, 0)
        participant.lastReadAt = previousRead
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(1L, 1L))
            .thenReturn(Optional.of(participant))
        whenever(chatMessageRepository.findVisibleByChatRoomIdAndCreatedAtAfter(eq(1L), org.mockito.kotlin.any()))
            .thenReturn(emptyList())

        chatService.markAsReadAndCollectUpdates(1L, 1L)

        verify(chatMessageRepository).findVisibleByChatRoomIdAndCreatedAtAfter(1L, previousRead)
    }
}
