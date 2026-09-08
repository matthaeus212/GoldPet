package com.goldpet.domain.gamification.service

import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.gamification.entity.DailyMissionSet
import com.goldpet.domain.gamification.entity.UserBadge
import com.goldpet.domain.gamification.repository.BadgeRepository
import com.goldpet.domain.gamification.repository.DailyMissionSetRepository
import com.goldpet.domain.gamification.repository.UserBadgeRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class DailyMissionServiceTest {

    @Mock private lateinit var dailyMissionSetRepository: DailyMissionSetRepository
    @Mock private lateinit var badgeRepository: BadgeRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var userBadgeRepository: UserBadgeRepository

    private lateinit var service: DailyMissionService

    private val walkBadge = badge(10L, "오늘의 산책", BadgeConditionType.WALK_DISTANCE_TOTAL)
    private val postBadge = badge(11L, "오늘의 소통", BadgeConditionType.COMMUNITY_POST)
    private val checkInBadge = badge(12L, "오늘의 체크인", BadgeConditionType.CHECK_IN)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = DailyMissionService(
            dailyMissionSetRepository, badgeRepository, userRepository, userBadgeRepository
        )
    }

    private fun badge(id: Long, name: String, type: BadgeConditionType) = Badge(
        id = id, name = name, description = name, imageUrl = "",
        conditionType = type, conditionValue = 1, rewardGold = 10,
        isRepeatable = true, repeatCycle = "DAILY"
    )

    @Test
    fun `rotation creates exactly one mission set per day when none exists`() {
        whenever(dailyMissionSetRepository.findByMissionDate(any())).thenReturn(null)
        whenever(badgeRepository.findAllByIsRepeatableTrueAndRepeatCycleAndIsActiveTrue("DAILY"))
            .thenReturn(listOf(walkBadge, postBadge, checkInBadge))
        whenever(dailyMissionSetRepository.saveAndFlush(any<DailyMissionSet>())).thenAnswer { it.getArgument(0) }

        service.rotateDailyMissions()

        val captor = argumentCaptor<DailyMissionSet>()
        verify(dailyMissionSetRepository).saveAndFlush(captor.capture())
        assertEquals(listOf(10L, 11L, 12L), captor.firstValue.badgeIds)
    }

    @Test
    fun `rotation is idempotent when set already exists for the day`() {
        val existing = DailyMissionSet(id = 1L, missionDate = LocalDate.now(), badgeIds = listOf(10L, 11L, 12L))
        whenever(dailyMissionSetRepository.findByMissionDate(any())).thenReturn(existing)

        service.rotateDailyMissions()

        verify(dailyMissionSetRepository, never()).saveAndFlush(any<DailyMissionSet>())
    }

    @Test
    fun `rotation selects only 3 of N candidates`() {
        val extra = badge(13L, "추가", BadgeConditionType.WALK_DISTANCE_TOTAL)
        whenever(dailyMissionSetRepository.findByMissionDate(any())).thenReturn(null)
        whenever(badgeRepository.findAllByIsRepeatableTrueAndRepeatCycleAndIsActiveTrue("DAILY"))
            .thenReturn(listOf(walkBadge, postBadge, checkInBadge, extra))
        whenever(dailyMissionSetRepository.saveAndFlush(any<DailyMissionSet>())).thenAnswer { it.getArgument(0) }

        service.rotateDailyMissions()

        val captor = argumentCaptor<DailyMissionSet>()
        verify(dailyMissionSetRepository).saveAndFlush(captor.capture())
        assertEquals(3, captor.firstValue.badgeIds.size)
    }

    @Test
    fun `getTodayMissions returns missions with completion progress`() {
        val today = LocalDate.now(DailyMissionService.KST)
        val set = DailyMissionSet(id = 1L, missionDate = today, badgeIds = listOf(10L, 11L, 12L))
        val completedBadge = UserBadge(
            id = 1L, user = testUser(), badge = walkBadge, currentValue = 3, cycleKey = today.toString()
        )

        whenever(userRepository.existsById(1L)).thenReturn(true)
        whenever(dailyMissionSetRepository.findByMissionDate(today)).thenReturn(set)
        whenever(badgeRepository.findAllById(listOf(10L, 11L, 12L)))
            .thenReturn(listOf(walkBadge, postBadge, checkInBadge))
        whenever(userBadgeRepository.findByUserIdAndCycleKey(1L, today.toString()))
            .thenReturn(listOf(completedBadge))

        val result = service.getTodayMissions(1L)

        assertEquals(today, result.missionDate)
        assertEquals(3, result.missions.size)
        val walkMission = result.missions.first { it.badgeId == 10L }
        assertTrue(walkMission.completed)
        assertEquals(3, walkMission.currentValue)
        val postMission = result.missions.first { it.badgeId == 11L }
        assertFalse(postMission.completed)
        assertNull(postMission.currentValue)
    }

    @Test
    fun `getTodayMissions throws when user not found`() {
        whenever(userRepository.existsById(999L)).thenReturn(false)

        assertThrows<NotFoundException> { service.getTodayMissions(999L) }
    }

    private fun testUser() = User(
        id = 1L, email = "u@example.com", oauthProvider = "LOCAL", oauthId = "u",
        username = "u", password = "p", nickname = "닉", name = "이름",
        birthDate = null, phoneNumber = null, gender = null, birthYear = null,
        mainLocationText = null, mainLocationGeom = null, profileImageUrl = null
    )
}
