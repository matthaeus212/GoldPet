package com.goldpet.domain.gamification.service

import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.gamification.entity.UserBadge
import com.goldpet.domain.gamification.repository.BadgeRepository
import com.goldpet.domain.gamification.repository.UserBadgeRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.common.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.*

class GamificationServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var badgeRepository: BadgeRepository

    @Mock
    private lateinit var userBadgeRepository: UserBadgeRepository

    private lateinit var gamificationService: GamificationService

    private lateinit var testUser: User
    private lateinit var testBadge: Badge

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        gamificationService = GamificationService(
            userRepository,
            badgeRepository,
            userBadgeRepository
        )

        testUser = createTestUser(1L, "testuser", "테스트유저")

        testBadge = Badge(
            id = 1L,
            name = "첫 산책",
            description = "첫 번째 산책 완료",
            imageUrl = "https://example.com/badge.png",
            conditionType = BadgeConditionType.WALK_COUNT,
            conditionValue = 1
        )
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
    fun `getAllBadgesWithStatus should return badges with acquired status`() {
        val userBadge = UserBadge(
            id = 1L,
            user = testUser,
            badge = testBadge
        ).apply {
            createdAt = LocalDateTime.now().minusDays(1)
        }

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(badgeRepository.findAllByIsActiveTrue()).thenReturn(listOf(testBadge))
        whenever(userBadgeRepository.findAllByUser(testUser)).thenReturn(listOf(userBadge))

        val result = gamificationService.getAllBadgesWithStatus(1L)

        assertEquals(1, result.size)
        assertEquals("첫 산책", result[0].name)
        assertNotNull(result[0].acquiredAt)
    }

    @Test
    fun `getAllBadgesWithStatus should return badges without acquired when not earned`() {
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(badgeRepository.findAllByIsActiveTrue()).thenReturn(listOf(testBadge))
        whenever(userBadgeRepository.findAllByUser(testUser)).thenReturn(emptyList())

        val result = gamificationService.getAllBadgesWithStatus(1L)

        assertEquals(1, result.size)
        assertNull(result[0].acquiredAt)
    }

    @Test
    fun `getMyBadges should return user badges`() {
        val userBadge = UserBadge(
            id = 1L,
            user = testUser,
            badge = testBadge
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userBadgeRepository.findAllByUser(testUser)).thenReturn(listOf(userBadge))

        val result = gamificationService.getMyBadges(1L)

        assertEquals(1, result.size)
        assertEquals("첫 산책", result[0].name)
    }

    @Test
    fun `getMyBadges should return empty list when no badges`() {
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userBadgeRepository.findAllByUser(testUser)).thenReturn(emptyList())

        val result = gamificationService.getMyBadges(1L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMyBadges should throw exception when user not found`() {
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            gamificationService.getMyBadges(999L)
        }
    }

    @Test
    fun `getAllBadgesWithStatus should use latest acquiredAt for repeatable badges`() {
        val repeatableBadge = Badge(
            id = 2L,
            name = "매일 산책",
            description = "매일 산책하기",
            imageUrl = "https://example.com/daily.png",
            conditionType = BadgeConditionType.WALK_COUNT,
            conditionValue = 1,
            isRepeatable = true,
            repeatCycle = "DAILY"
        )

        val olderBadge = UserBadge(
            id = 1L,
            user = testUser,
            badge = repeatableBadge,
            cycleKey = "2026-02-17"
        ).apply {
            createdAt = LocalDateTime.of(2026, 2, 17, 10, 0)
        }

        val newerBadge = UserBadge(
            id = 2L,
            user = testUser,
            badge = repeatableBadge,
            cycleKey = "2026-02-18"
        ).apply {
            createdAt = LocalDateTime.of(2026, 2, 18, 10, 0)
        }

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(badgeRepository.findAllByIsActiveTrue()).thenReturn(listOf(repeatableBadge))
        whenever(userBadgeRepository.findAllByUser(testUser)).thenReturn(listOf(olderBadge, newerBadge))

        val result = gamificationService.getAllBadgesWithStatus(1L)

        assertEquals(1, result.size)
        assertEquals(LocalDateTime.of(2026, 2, 18, 10, 0), result[0].acquiredAt)
    }
}
