package com.goldpet.domain.gold.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.common.exception.ForbiddenException
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.dto.ChargeRequest
import com.goldpet.domain.gold.dto.GoldProductResponse
import com.goldpet.domain.gold.dto.TransactionResponse
import com.goldpet.domain.gold.entity.TransactionStatus
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.gold.service.IdempotencyService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

/**
 * gold.payment.enabled 플래그가 충전(403 차단)과 상품 목록(빈 리스트) 동작을
 * 결정적으로 게이트하는지 컨트롤러 진입부 단위 검증.
 */
class GoldControllerPaymentGuardTest {

    @Mock private lateinit var goldService: GoldService
    @Mock private lateinit var idempotencyService: IdempotencyService
    @Mock private lateinit var systemSettingService: SystemSettingService

    private lateinit var controller: GoldController
    private val principal: UserPrincipal = mock { whenever(it.id).thenReturn(1L) }

    private val chargeRequest = ChargeRequest(amount = 100, paymentMethod = "IAP")

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        controller = GoldController(goldService, idempotencyService, systemSettingService)
    }

    private fun setPaymentFlag(value: String) {
        whenever(
            systemSettingService.getString(eq(GoldController.PAYMENT_FLAG_KEY), any())
        ).thenReturn(value)
    }

    private fun sampleTransaction() = TransactionResponse(
        id = 10L, type = TransactionType.CHARGE, amount = 100, balanceAfter = 100,
        description = "충전", status = TransactionStatus.COMPLETED, createdAt = LocalDateTime.now()
    )

    @Test
    fun `charge is blocked with Forbidden when payment flag is off`() {
        setPaymentFlag("false")

        assertThrows<ForbiddenException> {
            controller.chargeGold(principal, "idem-key", chargeRequest)
        }
        verify(goldService, never()).chargeGold(any(), any())
    }

    @Test
    fun `charge proceeds when payment flag is on`() {
        setPaymentFlag("true")
        val tx = sampleTransaction()
        whenever(idempotencyService.hashRequest(any(), any(), any())).thenReturn("hash")
        whenever(
            idempotencyService.execute(
                key = any(), userId = any(), requestHash = any(),
                responseType = eq(TransactionResponse::class.java), fn = any()
            )
        ).thenReturn(tx)

        val response = controller.chargeGold(principal, "idem-key", chargeRequest)

        assertEquals(200, response.statusCode.value())
        assertEquals(tx, response.body)
    }

    @Test
    fun `products returns empty list when payment flag is off`() {
        setPaymentFlag("false")

        val response = controller.getProducts()

        assertTrue(response.body!!.isEmpty())
        verify(goldService, never()).getProducts()
    }

    @Test
    fun `products returns service result when payment flag is on`() {
        setPaymentFlag("true")
        val products = listOf(
            GoldProductResponse(id = "p1", name = "100 골드", goldAmount = 100, price = 1000)
        )
        whenever(goldService.getProducts()).thenReturn(products)

        val response = controller.getProducts()

        assertEquals(products, response.body)
    }
}
