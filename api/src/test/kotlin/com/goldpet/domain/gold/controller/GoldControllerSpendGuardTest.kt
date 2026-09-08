// 범용 gold spend 엔드포인트가 클라이언트 지정 referenceType/referenceId 를 거부하는지 검증(EXT-CDX-011)
package com.goldpet.domain.gold.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.dto.SpendRequest
import com.goldpet.domain.gold.dto.TransactionResponse
import com.goldpet.domain.gold.entity.TransactionStatus
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.gold.service.IdempotencyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

/**
 * EXT-CDX-011 — 범용 `/gold/spend` 는 인증 사용자가 임의 referenceType/referenceId 로
 * 원장을 오염(내부 reference 사칭)시킬 수 있었다. 클라이언트가 reference 를 지정하면 거부한다.
 * 내부 도메인 흐름(ProfileBoost/AIProfile)은 서비스 계층을 직접 호출하므로 영향 없음.
 */
class GoldControllerSpendGuardTest {

    @Mock private lateinit var goldService: GoldService
    @Mock private lateinit var idempotencyService: IdempotencyService
    @Mock private lateinit var systemSettingService: SystemSettingService

    private lateinit var controller: GoldController
    private val principal: UserPrincipal = mock { whenever(it.id).thenReturn(1L) }

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        controller = GoldController(goldService, idempotencyService, systemSettingService)
    }

    private fun tx() = TransactionResponse(
        id = 1L, type = TransactionType.SPEND, amount = -100, balanceAfter = 0,
        description = "사용", status = TransactionStatus.COMPLETED, createdAt = LocalDateTime.now()
    )

    @Test
    fun `클라이언트가 referenceType 를 지정하면 400 으로 거부한다`() {
        val request = SpendRequest(amount = 100, description = "사용", referenceType = "AI_PROFILE")

        assertThrows<BadRequestException> {
            controller.spendGold(principal, null, request)
        }
        verify(goldService, never()).spendGold(any(), any())
    }

    @Test
    fun `클라이언트가 referenceId 를 지정하면 400 으로 거부한다`() {
        val request = SpendRequest(amount = 100, description = "사용", referenceId = 42L)

        assertThrows<BadRequestException> {
            controller.spendGold(principal, null, request)
        }
        verify(goldService, never()).spendGold(any(), any())
    }

    @Test
    fun `reference 없는 정상 spend 는 서비스로 전달된다`() {
        val request = SpendRequest(amount = 100, description = "프리미엄 기능")
        whenever(goldService.spendGold(any(), any())).thenReturn(tx())

        controller.spendGold(principal, null, request)

        verify(goldService).spendGold(1L, request)
    }
}
