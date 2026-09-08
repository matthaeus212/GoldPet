// 나간 참여자(leftAt≠null)를 인가에서 배제하는지 검증하는 회귀 테스트 (ARCH-005 이관분)
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

/**
 * WebSocketChatController 가 typing 인가를 위해 chatRoomParticipantRepository 를 직접 호출하던 시절,
 * "비필터 메서드(findByChatRoomIdAndUserId)를 실수로 쓰면 나간 참여자도 통과한다"는 함정을 잡는
 * 회귀 테스트가 컨트롤러 테스트에 있었다. ARCH-005 로 인가가 ChatService.isActiveParticipant 로
 * 이관됐으므로 그 검증도 여기로 옮긴다.
 */
class ChatServiceParticipantAuthzTest {

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
        )
        val room = ChatRoom(id = 1L, roomType = ChatRoomType.DIRECT)
        participant = ChatRoomParticipant(id = 1L, chatRoom = room, user = user, role = ChatRoomRole.MEMBER)
    }

    @Test
    fun `active participant is authorized`() {
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(1L, 1L))
            .thenReturn(Optional.of(participant))

        assertTrue(chatService.isActiveParticipant(1L, 1L))
    }

    // 함정: 비필터 메서드는 일부러 present 로 스텁한다. 서비스가 실수로 그 메서드를 쓰면
    // 나간 참여자가 통과해버리므로, 이 테스트가 false 를 요구해 필터 누락을 잡는다.
    @Test
    fun `participant who left the room is rejected even though a stale row exists`() {
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserId(1L, 1L))
            .thenReturn(Optional.of(participant))
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(1L, 1L))
            .thenReturn(Optional.empty())

        assertFalse(chatService.isActiveParticipant(1L, 1L))
        verify(chatRoomParticipantRepository, never()).findByChatRoomIdAndUserId(1L, 1L)
    }

    @Test
    fun `non participant is rejected`() {
        whenever(chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(1L, 1L))
            .thenReturn(Optional.empty())

        assertFalse(chatService.isActiveParticipant(1L, 1L))
    }
}
