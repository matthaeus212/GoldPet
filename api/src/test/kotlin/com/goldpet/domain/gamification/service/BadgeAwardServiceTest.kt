package com.goldpet.domain.gamification.service

import com.goldpet.domain.checkin.repository.CheckInRepository
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.community.repository.CommunityCommentRepository
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.friend.repository.MatchRepository
import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.gamification.entity.UserBadge
import com.goldpet.domain.gamification.repository.BadgeRepository
import com.goldpet.domain.gamification.repository.UserBadgeRepository
import com.goldpet.domain.gold.entity.GoldTransaction
import com.goldpet.domain.gold.repository.GoldTransactionRepository
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.repository.WalkRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.util.*

class BadgeAwardServiceTest {

    @Mock private lateinit var badgeRepository: BadgeRepository
    @Mock private lateinit var userBadgeRepository: UserBadgeRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var petRepository: PetRepository
    @Mock private lateinit var walkRepository: WalkRepository
    @Mock private lateinit var communityPostRepository: CommunityPostRepository
    @Mock private lateinit var communityCommentRepository: CommunityCommentRepository
    @Mock private lateinit var checkInRepository: CheckInRepository
    @Mock private lateinit var matchRepository: MatchRepository
    @Mock private lateinit var goldTransactionRepository: GoldTransactionRepository
    @Mock private lateinit var systemSettingService: SystemSettingService

    private lateinit var service: BadgeAwardService
    private lateinit var user: User
    private lateinit var dailyBadge: Badge

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = BadgeAwardService(
            badgeRepository, userBadgeRepository, userRepository, petRepository, walkRepository,
            communityPostRepository, communityCommentRepository, checkInRepository, matchRepository,
            goldTransactionRepository, systemSettingService
        )
        user = User(
            id = 1L, email = "u@example.com", oauthProvider = "LOCAL", oauthId = "u",
            username = "u", password = "p", nickname = "닉", name = "이름",
            birthDate = null, phoneNumber = null, gender = null, birthYear = null,
            mainLocationText = null, mainLocationGeom = null, profileImageUrl = null
        ).apply { goldBalance = 100 }

        dailyBadge = Badge(
            id = 10L, name = "오늘의 체크인", description = "체크인", imageUrl = "",
            conditionType = BadgeConditionType.CHECK_IN, conditionValue = 1,
            rewardGold = 10, isRepeatable = true, repeatCycle = "DAILY"
        )

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user))
        whenever(badgeRepository.findAllByConditionTypeAndIsActiveTrue(BadgeConditionType.CHECK_IN))
            .thenReturn(listOf(dailyBadge))
        whenever(checkInRepository.countByUserId(1L)).thenReturn(5L) // >= conditionValue
        whenever(userBadgeRepository.existsByUserAndBadgeAndCycleKey(any(), any(), any())).thenReturn(false)
        whenever(userBadgeRepository.saveAndFlush(any<UserBadge>())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `daily mission gold is capped when prior daily gold near cap`() {
        whenever(systemSettingService.getInt(eq("mission.daily.gold_cap"), any())).thenReturn(20)
        // 이미 오늘 15골드 받음 → reward 10이지만 5만 지급 (cap 20).
        whenever(userBadgeRepository.sumDailyRewardGoldGiven(eq(1L), any())).thenReturn(15L)

        val awarded = service.checkAndAwardBadges(1L, BadgeConditionType.CHECK_IN)

        assertEquals(1, awarded.size)
        assertEquals(105, user.goldBalance) // 100 + 5 (capped)
        val captor = argumentCaptor<GoldTransaction>()
        verify(goldTransactionRepository).save(captor.capture())
        assertEquals(5, captor.firstValue.amount)
    }

    @Test
    fun `daily mission gold fully skipped when cap already reached`() {
        whenever(systemSettingService.getInt(eq("mission.daily.gold_cap"), any())).thenReturn(20)
        whenever(userBadgeRepository.sumDailyRewardGoldGiven(eq(1L), any())).thenReturn(20L)

        val awarded = service.checkAndAwardBadges(1L, BadgeConditionType.CHECK_IN)

        assertEquals(1, awarded.size) // 배지는 부여되나 골드는 0.
        assertEquals(100, user.goldBalance)
        verify(goldTransactionRepository, never()).save(any())
    }

    @Test
    fun `daily mission grants full reward when under cap`() {
        whenever(systemSettingService.getInt(eq("mission.daily.gold_cap"), any())).thenReturn(20)
        whenever(userBadgeRepository.sumDailyRewardGoldGiven(eq(1L), any())).thenReturn(0L)

        service.checkAndAwardBadges(1L, BadgeConditionType.CHECK_IN)

        assertEquals(110, user.goldBalance) // 100 + 10
    }

    @Test
    fun `concurrent award DataIntegrityViolationException skips gold`() {
        whenever(userBadgeRepository.saveAndFlush(any<UserBadge>()))
            .thenThrow(DataIntegrityViolationException("duplicate key (user_id, badge_id, cycle_key)"))

        val awarded = service.checkAndAwardBadges(1L, BadgeConditionType.CHECK_IN)

        assertTrue(awarded.isEmpty())
        assertEquals(100, user.goldBalance) // 변화 없음
        verify(goldTransactionRepository, never()).save(any())
    }
}
