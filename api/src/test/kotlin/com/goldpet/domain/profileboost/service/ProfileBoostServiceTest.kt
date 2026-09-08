package com.goldpet.domain.profileboost.service

import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.dto.SpendRequest
import com.goldpet.domain.gold.dto.TransactionResponse
import com.goldpet.domain.gold.entity.TransactionStatus
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.profileboost.entity.ProfileBoost
import com.goldpet.domain.profileboost.repository.ProfileBoostRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class ProfileBoostServiceTest {

    @Mock private lateinit var profileBoostRepository: ProfileBoostRepository
    @Mock private lateinit var goldService: GoldService
    @Mock private lateinit var systemSettingService: SystemSettingService

    private lateinit var service: ProfileBoostService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = ProfileBoostService(profileBoostRepository, goldService, systemSettingService)
        whenever(systemSettingService.getInt(eq(ProfileBoostService.COST_KEY), any())).thenReturn(30)
        whenever(systemSettingService.getInt(eq(ProfileBoostService.DURATION_KEY), any())).thenReturn(30)
    }

    private fun spendResponse(amount: Int) = TransactionResponse(
        id = 1L, type = TransactionType.SPEND, amount = -amount, balanceAfter = 70,
        description = "프로필 노출 부스트", status = TransactionStatus.COMPLETED, createdAt = LocalDateTime.now()
    )

    @Test
    fun `purchaseBoost deducts gold and creates an active boost`() {
        whenever(goldService.spendGold(eq(1L), any())).thenReturn(spendResponse(30))
        whenever(profileBoostRepository.save(any<ProfileBoost>())).thenAnswer { it.getArgument(0) }

        val result = service.purchaseBoost(1L)

        val captor = argumentCaptor<SpendRequest>()
        verify(goldService).spendGold(eq(1L), captor.capture())
        assertEquals(30, captor.firstValue.amount)
        assertEquals(ProfileBoostService.REFERENCE_TYPE, captor.firstValue.referenceType)

        assertEquals(30, result.goldCost)
        assertTrue(result.active)
        assertTrue(result.remainingSeconds > 0)
        verify(profileBoostRepository).save(any<ProfileBoost>())
    }

    @Test
    fun `purchaseBoost rejects when balance insufficient and creates no boost`() {
        whenever(goldService.spendGold(eq(1L), any()))
            .thenThrow(BadRequestException("골드 잔액이 부족합니다."))

        assertThrows<BadRequestException> { service.purchaseBoost(1L) }

        verify(profileBoostRepository, never()).save(any<ProfileBoost>())
    }

    @Test
    fun `getActiveBoost returns active boost when present`() {
        val now = LocalDateTime.now()
        val boost = ProfileBoost(
            id = 5L, userId = 1L, startedAt = now.minusMinutes(5),
            expiresAt = now.plusMinutes(25), goldCost = 30
        )
        whenever(profileBoostRepository.findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(eq(1L), any()))
            .thenReturn(boost)

        val result = service.getActiveBoost(1L)

        assertTrue(result.active)
        assertNotNull(result.boost)
        assertEquals(5L, result.boost!!.id)
    }

    @Test
    fun `getActiveBoost returns inactive when no active boost`() {
        whenever(profileBoostRepository.findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(eq(1L), any()))
            .thenReturn(null)

        val result = service.getActiveBoost(1L)

        assertFalse(result.active)
        assertNull(result.boost)
    }

    @Test
    fun `boostedUserIds returns ids of users with unexpired boosts`() {
        val now = LocalDateTime.now()
        whenever(profileBoostRepository.findAllByExpiresAtAfter(any())).thenReturn(
            listOf(
                ProfileBoost(id = 1L, userId = 7L, startedAt = now, expiresAt = now.plusMinutes(10), goldCost = 30),
                ProfileBoost(id = 2L, userId = 9L, startedAt = now, expiresAt = now.plusMinutes(10), goldCost = 30)
            )
        )

        val result = service.boostedUserIds(now)

        assertEquals(setOf(7L, 9L), result)
    }
}
