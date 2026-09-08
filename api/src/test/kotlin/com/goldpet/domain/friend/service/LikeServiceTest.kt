package com.goldpet.domain.friend.service

import com.goldpet.domain.friend.entity.Like
import com.goldpet.domain.friend.entity.LikeStatus
import com.goldpet.domain.friend.entity.Match
import com.goldpet.domain.friend.repository.LikeRepository
import com.goldpet.domain.friend.repository.MatchRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.friend.event.LikeNotificationEvent
import com.goldpet.domain.metrics.entity.LikeEventAction
import com.goldpet.domain.metrics.service.LikeEventRecorder
import com.goldpet.domain.notification.entity.NotificationType
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import com.goldpet.domain.common.exception.*
import java.time.LocalDateTime
import java.util.*

class LikeServiceTest {

    @Mock
    private lateinit var likeRepository: LikeRepository

    @Mock
    private lateinit var matchRepository: MatchRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var userBlockRepository: UserBlockRepository

    @Mock
    private lateinit var likeEventRecorder: LikeEventRecorder

    private lateinit var likeService: LikeService

    private lateinit var testUser: User
    private lateinit var targetUser: User

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        likeService = LikeService(likeRepository, matchRepository, userRepository, eventPublisher, userBlockRepository, likeEventRecorder)

        testUser = createTestUser(1L, "testuser", "테스트유저")
        targetUser = createTestUser(2L, "targetuser", "대상유저")
    }

    private fun createTestUser(id: Long, oauthId: String, nickname: String): User {
        return User(
            id = id,
            email = "$oauthId@example.com",
            oauthProvider = "LOCAL",
            oauthId = oauthId,
            username = oauthId,
            password = "password",
            nickname = nickname,
            name = nickname,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )
    }

    @Test
    fun `likeUser should create new like when not exists`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(targetUser))
        whenever(likeRepository.findByFromUserAndToUser(testUser, targetUser)).thenReturn(Optional.empty())
        whenever(likeRepository.existsByFromUserIdAndToUserIdAndStatus(2L, 1L, LikeStatus.ACTIVE)).thenReturn(false)
        whenever(likeRepository.save(any<Like>())).thenAnswer { invocation ->
            val like = invocation.getArgument<Like>(0)
            Like(
                id = 1L,
                fromUser = like.fromUser,
                toUser = like.toUser,
                status = like.status
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }
        // When
        val result = likeService.likeUser(1L, 2L)

        // Then
        assertNotNull(result)
        assertEquals(2L, result.toUserId)
        assertFalse(result.isMutual)
        // like_events (W1(2)): one LIKE row, is_match=false (not mutual)
        verify(likeEventRecorder).record(1L, 2L, LikeEventAction.LIKE, false, null)
        // PERF-006: 알림은 동기 createNotification 대신 커밋 후 비동기 이벤트로 발행된다.
        //  非상호 → LIKE 타입, 수신자=대상, 발신자=나. (내용 불변, 타이밍만 분리)
        val captor = argumentCaptor<LikeNotificationEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        val req = captor.firstValue.request
        assertEquals(NotificationType.LIKE, req.type)
        assertEquals(2L, req.userId)
        assertEquals(1L, req.senderId)
    }

    @Test
    fun `likeUser should detect mutual like`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(targetUser))
        whenever(likeRepository.findByFromUserAndToUser(testUser, targetUser)).thenReturn(Optional.empty())
        whenever(likeRepository.existsByFromUserIdAndToUserIdAndStatus(2L, 1L, LikeStatus.ACTIVE)).thenReturn(true)
        whenever(matchRepository.findByUser1AndUser2(testUser, targetUser)).thenReturn(Optional.empty())
        whenever(matchRepository.save(any<Match>())).thenAnswer { invocation -> invocation.getArgument(0) }
        whenever(likeRepository.save(any<Like>())).thenAnswer { invocation ->
            val like = invocation.getArgument<Like>(0)
            Like(
                id = 1L,
                fromUser = like.fromUser,
                toUser = like.toUser,
                status = like.status
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }
        // When
        val result = likeService.likeUser(1L, 2L)

        // Then
        assertTrue(result.isMutual)
        // like_events (W1(2)): one LIKE row, is_match=true (this like produced a match now)
        verify(likeEventRecorder).record(1L, 2L, LikeEventAction.LIKE, true, null)
        // PERF-006: 상호 좋아요 → MATCH 타입 알림 이벤트 발행(커밋 후 비동기).
        val captor = argumentCaptor<LikeNotificationEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(NotificationType.MATCH, captor.firstValue.request.type)
    }

    @Test
    fun `likeUser should reactivate canceled like`() {
        // Given
        val canceledLike = Like(
            id = 1L,
            fromUser = testUser,
            toUser = targetUser,
            status = LikeStatus.CANCELED
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(targetUser))
        whenever(likeRepository.findByFromUserAndToUser(testUser, targetUser)).thenReturn(Optional.of(canceledLike))
        whenever(likeRepository.existsByFromUserIdAndToUserIdAndStatus(2L, 1L, LikeStatus.ACTIVE)).thenReturn(false)

        // When
        val result = likeService.likeUser(1L, 2L)

        // Then
        assertEquals(LikeStatus.ACTIVE, canceledLike.status)
        assertFalse(result.isMutual)
    }

    @Test
    fun `likeUser should throw exception when target user not found`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows(NotFoundException::class.java) {
            likeService.likeUser(1L, 999L)
        }
    }

    @Test
    fun `unlikeUser should cancel like`() {
        // Given
        val existingLike = Like(
            id = 1L,
            fromUser = testUser,
            toUser = targetUser,
            status = LikeStatus.ACTIVE
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(targetUser))
        whenever(likeRepository.findByFromUserAndToUser(testUser, targetUser)).thenReturn(Optional.of(existingLike))
        whenever(likeRepository.findByFromUserAndToUser(targetUser, testUser)).thenReturn(Optional.empty())
        whenever(likeRepository.save(any<Like>())).thenReturn(existingLike)
        whenever(matchRepository.findByUserIds(1L, 2L)).thenReturn(Optional.empty())

        // When
        val result = likeService.unlikeUser(1L, 2L)

        // Then
        assertTrue(result)
        assertEquals(LikeStatus.CANCELED, existingLike.status)
        // like_events (W1(2)): one CANCEL row, is_match=false (cancel never produces a match)
        verify(likeEventRecorder).record(1L, 2L, LikeEventAction.CANCEL, false, null)
    }

    @Test
    fun `unlikeUser should return false when like not found`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(targetUser))
        whenever(likeRepository.findByFromUserAndToUser(testUser, targetUser)).thenReturn(Optional.empty())

        // When
        val result = likeService.unlikeUser(1L, 2L)

        // Then
        assertFalse(result)
    }

    @Test
    fun `rejectLike should cancel received like`() {
        // Given - user 1 rejects like from user 2
        val receivedLike = Like(
            id = 1L,
            fromUser = targetUser,  // sender (user 2)
            toUser = testUser,      // receiver (user 1)
            status = LikeStatus.ACTIVE
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(targetUser))
        whenever(likeRepository.findByFromUserAndToUser(targetUser, testUser)).thenReturn(Optional.of(receivedLike))
        whenever(likeRepository.save(any<Like>())).thenReturn(receivedLike)

        // When - user 1 rejects like from sender 2
        val result = likeService.rejectLike(1L, 2L)

        // Then
        assertTrue(result)
        assertEquals(LikeStatus.CANCELED, receivedLike.status)
    }

    @Test
    fun `getMyLikes should return sent likes users`() {
        // Given
        val sentLike = Like(
            id = 1L,
            fromUser = testUser,
            toUser = targetUser,
            status = LikeStatus.ACTIVE
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(likeRepository.findSentOnlyLikes(1L)).thenReturn(listOf(sentLike))

        // When
        val result = likeService.getMyLikes(1L)

        // Then
        assertEquals(1, result.size)
        assertEquals(2L, result[0].id)
    }

    @Test
    fun `getMutualLikes should return mutual likes users`() {
        // Given
        val mutualLike = Like(
            id = 1L,
            fromUser = testUser,
            toUser = targetUser,
            status = LikeStatus.ACTIVE
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(likeRepository.findMutualLikes(1L)).thenReturn(listOf(mutualLike))

        // When
        val result = likeService.getMutualLikes(1L)

        // Then
        assertEquals(1, result.size)
    }

    @Test
    fun `getReceivedLikes should return received likes users`() {
        // Given
        val receivedLike = Like(
            id = 1L,
            fromUser = targetUser,
            toUser = testUser,
            status = LikeStatus.ACTIVE
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(likeRepository.findReceivedOnlyLikes(1L)).thenReturn(listOf(receivedLike))

        // When
        val result = likeService.getReceivedLikes(1L)

        // Then
        assertEquals(1, result.size)
        assertEquals(2L, result[0].id)
    }

    @Test
    fun `hasLiked should return true when like exists`() {
        // Given
        whenever(likeRepository.existsByFromUserIdAndToUserIdAndStatus(1L, 2L, LikeStatus.ACTIVE)).thenReturn(true)

        // When
        val result = likeService.hasLiked(1L, 2L)

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasLiked should return false when like does not exist`() {
        // Given
        whenever(likeRepository.existsByFromUserIdAndToUserIdAndStatus(1L, 2L, LikeStatus.ACTIVE)).thenReturn(false)

        // When
        val result = likeService.hasLiked(1L, 2L)

        // Then
        assertFalse(result)
    }
}
