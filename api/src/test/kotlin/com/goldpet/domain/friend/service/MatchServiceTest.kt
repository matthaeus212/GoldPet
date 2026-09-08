package com.goldpet.domain.friend.service

import com.goldpet.domain.friend.entity.Like
import com.goldpet.domain.friend.entity.Match
import com.goldpet.domain.friend.repository.LikeRepository
import com.goldpet.domain.friend.repository.MatchRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.chat.repository.ChatRoomRepository
import com.goldpet.domain.metrics.entity.LikeEventAction
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import com.goldpet.domain.common.exception.*
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class MatchServiceTest {

    @Mock
    private lateinit var likeRepository: LikeRepository

    @Mock
    private lateinit var matchRepository: MatchRepository

    @Mock
    private lateinit var userBlockRepository: UserBlockRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var badgeAwardService: com.goldpet.domain.gamification.service.BadgeAwardService

    @Mock
    private lateinit var petRepository: com.goldpet.domain.pet.repository.PetRepository

    @Mock
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Mock
    private lateinit var likeEventRecorder: com.goldpet.domain.metrics.service.LikeEventRecorder

    @InjectMocks
    private lateinit var matchService: MatchService

    private lateinit var user1: User
    private lateinit var user2: User

    @BeforeEach
    fun setUp() {
        user1 = User(
            id = 1L,
            email = "user1@example.com",
            oauthProvider = "GOOGLE",
            oauthId = "google1",
            username = null,
            password = null,
            nickname = "User1",
            name = null,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )
        user2 = User(
            id = 2L,
            email = "user2@example.com",
            oauthProvider = "GOOGLE",
            oauthId = "google2",
            username = null,
            password = null,
            nickname = "User2",
            name = null,
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
    fun `likeUser_shouldCreateLike_whenNoMutualLikeExists`() {
        // Given
        `when`(userRepository.findById(user1.id)).thenReturn(Optional.of(user1))
        `when`(userRepository.findById(user2.id)).thenReturn(Optional.of(user2))
        `when`(userBlockRepository.existsByBlockerAndBlocked(user2, user1)).thenReturn(false)
        `when`(likeRepository.findByFromUserAndToUser(user1, user2)).thenReturn(Optional.empty())
        `when`(likeRepository.findByFromUserAndToUser(user2, user1)).thenReturn(Optional.empty())
        `when`(likeRepository.save(any(Like::class.java))).thenAnswer {
            (it.arguments[0] as Like).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val isMatchCreated = matchService.likeUser(user1.id, user2.id)

        // Then
        verify(likeRepository, times(1)).save(any(Like::class.java))
        verify(matchRepository, never()).save(any(Match::class.java))
        assertThat(isMatchCreated).isFalse()
        // second seam (guardrail #1): like_events LIKE row with is_match=false (no match produced)
        verify(likeEventRecorder, times(1)).record(user1.id, user2.id, LikeEventAction.LIKE, false, null)
    }

    @Test
    fun `likeUser_shouldCreateMatch_whenMutualLikeExists`() {
        // Given
        `when`(userRepository.findById(user1.id)).thenReturn(Optional.of(user1))
        `when`(userRepository.findById(user2.id)).thenReturn(Optional.of(user2))
        `when`(userBlockRepository.existsByBlockerAndBlocked(user2, user1)).thenReturn(false)
        `when`(likeRepository.findByFromUserAndToUser(user1, user2)).thenReturn(Optional.empty())
        `when`(likeRepository.findByFromUserAndToUser(user2, user1)).thenReturn(Optional.of(Like(fromUser = user2, toUser = user1).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }))
        `when`(likeRepository.save(any(Like::class.java))).thenAnswer {
            (it.arguments[0] as Like).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }
        `when`(matchRepository.findByUser1AndUser2(user1, user2)).thenReturn(Optional.empty())
        `when`(matchRepository.save(any(Match::class.java))).thenAnswer {
            (it.arguments[0] as Match).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val isMatchCreated = matchService.likeUser(user1.id, user2.id)

        // Then
        verify(likeRepository, times(1)).save(any(Like::class.java))
        verify(matchRepository, times(1)).save(any(Match::class.java))
        assertThat(isMatchCreated).isTrue()
        // second seam (guardrail #1): like_events LIKE row with is_match=true (mutual produced a match now)
        verify(likeEventRecorder, times(1)).record(user1.id, user2.id, LikeEventAction.LIKE, true, null)
    }

    @Test
    fun `likeUser_shouldNotCreateDuplicateLike`() {
        // Given
        `when`(userRepository.findById(user1.id)).thenReturn(Optional.of(user1))
        `when`(userRepository.findById(user2.id)).thenReturn(Optional.of(user2))
        `when`(userBlockRepository.existsByBlockerAndBlocked(user2, user1)).thenReturn(false)
        `when`(likeRepository.findByFromUserAndToUser(user1, user2)).thenReturn(Optional.of(Like(fromUser = user1, toUser = user2).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }))

        // When
        val isMatchCreated = matchService.likeUser(user1.id, user2.id)

        // Then
        verify(likeRepository, never()).save(any(Like::class.java))
        verify(matchRepository, never()).save(any(Match::class.java))
        assertThat(isMatchCreated).isFalse()
        // no new like row → no like_events append
        verifyNoInteractions(likeEventRecorder)
    }

    @Test
    fun `likeUser_shouldThrowException_whenUserNotFound`() {
        // Given
        `when`(userRepository.findById(user1.id)).thenReturn(Optional.empty())

        // When / Then
        assertThrows<NotFoundException> {
            matchService.likeUser(user1.id, user2.id)
        }
        verify(likeRepository, never()).save(any(Like::class.java))
        verify(matchRepository, never()).save(any(Match::class.java))
    }

    @Test
    fun `likeUser_shouldThrowException_whenBlocked`() {
        // Given
        `when`(userRepository.findById(user1.id)).thenReturn(Optional.of(user1))
        `when`(userRepository.findById(user2.id)).thenReturn(Optional.of(user2))
        `when`(userBlockRepository.existsByBlockerAndBlocked(user2, user1)).thenReturn(true)

        // When / Then
        assertThrows<ForbiddenException> {
            matchService.likeUser(user1.id, user2.id)
        }
        verify(likeRepository, never()).save(any(Like::class.java))
    }
}
