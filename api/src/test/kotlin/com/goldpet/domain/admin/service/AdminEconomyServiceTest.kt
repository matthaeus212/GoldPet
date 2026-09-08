package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.AdminRefundRequest
import com.goldpet.domain.admin.controller.GoldAdjustRequest
import com.goldpet.domain.admin.controller.GoldProductCreateRequest
import com.goldpet.domain.admin.controller.GoldProductUpdateRequest
import com.goldpet.domain.gold.entity.GoldProduct
import com.goldpet.domain.gold.entity.GoldTransaction
import com.goldpet.domain.gold.entity.TransactionStatus
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.repository.GoldProductRepository
import com.goldpet.domain.gold.repository.GoldTransactionRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.common.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.*

class AdminEconomyServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var goldTransactionRepository: GoldTransactionRepository

    @Mock
    private lateinit var goldProductRepository: GoldProductRepository

    private lateinit var service: AdminEconomyService

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = AdminEconomyService(userRepository, goldTransactionRepository, goldProductRepository)

        testUser = User(
            id = 1L,
            email = "test@example.com",
            oauthProvider = "LOCAL",
            oauthId = "testuser",
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
        testUser.goldBalance = 1000
    }

    // === adjustGold Tests ===

    @Test
    fun `adjustGold should create ADMIN_ADJUST transaction for positive amount`() {
        val request = GoldAdjustRequest(userId = 1L, amount = 500, reason = "이벤트 보상")

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(goldTransactionRepository.save(any<GoldTransaction>())).thenAnswer { it.getArgument(0) }

        service.adjustGold(request)

        assertEquals(1500, testUser.goldBalance)
    }

    @Test
    fun `adjustGold should create ADMIN_ADJUST transaction for negative amount`() {
        val request = GoldAdjustRequest(userId = 1L, amount = -200, reason = "부정 사용 차감")

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(goldTransactionRepository.save(any<GoldTransaction>())).thenAnswer { it.getArgument(0) }

        service.adjustGold(request)

        assertEquals(800, testUser.goldBalance)
    }

    @Test
    fun `adjustGold should throw when balance goes below zero`() {
        val request = GoldAdjustRequest(userId = 1L, amount = -2000, reason = "과도한 차감")

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))

        assertThrows<BadRequestException> {
            service.adjustGold(request)
        }
    }

    @Test
    fun `adjustGold should throw when user not found`() {
        val request = GoldAdjustRequest(userId = 999L, amount = 100, reason = "테스트")

        whenever(userRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.adjustGold(request)
        }
    }

    // === refundTransaction Tests ===

    @Test
    fun `refundTransaction should refund SPEND transaction`() {
        val spendTx = GoldTransaction(
            id = 10L,
            user = testUser,
            type = TransactionType.SPEND,
            amount = -500,
            balanceAfter = 500,
            description = "AI 프로필",
            status = TransactionStatus.COMPLETED
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        val request = AdminRefundRequest(transactionId = 10L, reason = "고객 불만")

        whenever(goldTransactionRepository.findById(10L)).thenReturn(Optional.of(spendTx))
        whenever(goldTransactionRepository.existsByOriginalTransactionId(10L)).thenReturn(false)
        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(goldTransactionRepository.save(any<GoldTransaction>())).thenAnswer { it.getArgument(0) }

        service.refundTransaction(request)

        assertEquals(1500, testUser.goldBalance) // 1000 + 500
    }

    @Test
    fun `refundTransaction should throw for non-SPEND transaction`() {
        val chargeTx = GoldTransaction(
            id = 10L,
            user = testUser,
            type = TransactionType.CHARGE,
            amount = 500,
            balanceAfter = 1500,
            description = "충전",
            status = TransactionStatus.COMPLETED
        )

        val request = AdminRefundRequest(transactionId = 10L, reason = "테스트")

        whenever(goldTransactionRepository.findById(10L)).thenReturn(Optional.of(chargeTx))

        val ex = assertThrows<BadRequestException> {
            service.refundTransaction(request)
        }
        assertEquals("Only SPEND transactions can be refunded", ex.message)
    }

    @Test
    fun `refundTransaction should throw for already refunded transaction`() {
        val spendTx = GoldTransaction(
            id = 10L,
            user = testUser,
            type = TransactionType.SPEND,
            amount = -500,
            balanceAfter = 500,
            description = "테스트",
            status = TransactionStatus.COMPLETED
        )

        val request = AdminRefundRequest(transactionId = 10L, reason = "테스트")

        whenever(goldTransactionRepository.findById(10L)).thenReturn(Optional.of(spendTx))
        whenever(goldTransactionRepository.existsByOriginalTransactionId(10L)).thenReturn(true)

        val ex = assertThrows<ConflictException> {
            service.refundTransaction(request)
        }
        assertEquals("Transaction already refunded", ex.message)
    }

    // === Product CRUD Tests ===

    @Test
    fun `createProduct should create and return product`() {
        val request = GoldProductCreateRequest(
            productCode = "gold_100",
            name = "골드 100개",
            goldAmount = 100,
            price = 10000,
            bonus = 10,
            displayOrder = 1
        )

        whenever(goldProductRepository.findByProductCode("gold_100")).thenReturn(null)
        whenever(goldProductRepository.save(any<GoldProduct>())).thenAnswer { invocation ->
            val p = invocation.getArgument<GoldProduct>(0)
            GoldProduct(
                id = 1L,
                productCode = p.productCode,
                name = p.name,
                goldAmount = p.goldAmount,
                price = p.price,
                bonus = p.bonus,
                displayOrder = p.displayOrder
            )
        }

        val result = service.createProduct(request)

        assertEquals("gold_100", result.productCode)
        assertEquals("골드 100개", result.name)
        assertEquals(100, result.goldAmount)
        assertEquals(10, result.bonus)
    }

    @Test
    fun `createProduct should throw for duplicate product code`() {
        val request = GoldProductCreateRequest(
            productCode = "gold_100",
            name = "골드 100개",
            goldAmount = 100,
            price = 10000
        )

        whenever(goldProductRepository.findByProductCode("gold_100")).thenReturn(
            GoldProduct(id = 1L, productCode = "gold_100", name = "기존", goldAmount = 100, price = 10000)
        )

        val ex = assertThrows<ConflictException> {
            service.createProduct(request)
        }
        assertTrue(ex.message!!.contains("already exists"))
    }

    @Test
    fun `deleteProduct should soft delete by setting isActive false`() {
        val product = GoldProduct(id = 1L, productCode = "gold_100", name = "골드 100", goldAmount = 100, price = 10000)
        product.isActive = true

        whenever(goldProductRepository.findById(1L)).thenReturn(Optional.of(product))
        whenever(goldProductRepository.save(any<GoldProduct>())).thenAnswer { it.getArgument(0) }

        service.deleteProduct(1L)

        assertFalse(product.isActive)
    }
}
