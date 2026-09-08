package com.goldpet.domain.gold.service

import com.goldpet.domain.common.exception.*
import com.goldpet.domain.gold.dto.ChargeRequest
import com.goldpet.domain.gold.dto.RefundRequest
import com.goldpet.domain.gold.dto.SpendRequest
import com.goldpet.domain.gold.entity.GoldProduct
import com.goldpet.domain.gold.entity.GoldTransaction
import com.goldpet.domain.gold.entity.TransactionStatus
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.repository.GoldProductRepository
import com.goldpet.domain.gold.repository.GoldTransactionRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.*

class GoldServiceTest {

    @Mock
    private lateinit var transactionRepository: GoldTransactionRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var productRepository: GoldProductRepository

    @Mock
    private lateinit var systemSettingService: SystemSettingService

    private lateinit var goldService: GoldService

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        goldService = GoldService(transactionRepository, userRepository, productRepository, systemSettingService)

        testUser = createTestUser(1L, "testuser", "테스트유저")
        testUser.goldBalance = 1000
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
    fun `getBalance should return user gold balance with stats`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(transactionRepository.sumAmountByUserAndTypeSince(any(), any(), any())).thenReturn(500)

        // When
        val result = goldService.getBalance(1L)

        // Then
        assertEquals(1000, result.balance)
    }

    @Test
    fun `getBalance should throw exception when user not found`() {
        // Given
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            goldService.getBalance(999L)
        }
    }

    @Test
    fun `getProducts should return product list from repository`() {
        // Given
        val product = GoldProduct(
            id = 1L,
            productCode = "gold_10",
            name = "골드 10개",
            goldAmount = 10,
            price = 1000,
            bonus = 0,
            displayOrder = 1
        )
        whenever(productRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()).thenReturn(listOf(product))

        // When
        val result = goldService.getProducts()

        // Then
        assertTrue(result.isNotEmpty())
        assertEquals("gold_10", result[0].id)
        assertEquals(10, result[0].goldAmount)
    }

    @Test
    fun `chargeGold should add gold to user balance`() {
        // Given
        val request = ChargeRequest(
            amount = 100,
            paymentMethod = "CARD",
            externalTransactionId = "ext_123"
        )

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(transactionRepository.save(any<GoldTransaction>())).thenAnswer { invocation ->
            val transaction = invocation.getArgument<GoldTransaction>(0)
            GoldTransaction(
                id = 1L,
                user = transaction.user,
                type = transaction.type,
                amount = transaction.amount,
                balanceAfter = transaction.balanceAfter,
                description = transaction.description,
                paymentMethod = transaction.paymentMethod,
                externalTransactionId = transaction.externalTransactionId,
                status = TransactionStatus.COMPLETED
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = goldService.chargeGold(1L, request)

        // Then
        assertNotNull(result)
        assertEquals(100, result.amount)
        assertEquals(TransactionType.CHARGE, result.type)
        assertEquals(1100, testUser.goldBalance)  // 1000 + 100
    }

    @Test
    fun `chargeGold should throw exception when amount is not positive`() {
        // Given
        val request = ChargeRequest(amount = 0, paymentMethod = "CARD")
        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))

        // When & Then
        assertThrows<BadRequestException> {
            goldService.chargeGold(1L, request)
        }
    }

    @Test
    fun `chargeGold should throw exception when user not found`() {
        // Given
        val request = ChargeRequest(amount = 100, paymentMethod = "CARD")
        whenever(userRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            goldService.chargeGold(999L, request)
        }
    }

    @Test
    fun `spendGold should deduct gold from user balance`() {
        // Given
        val request = SpendRequest(
            amount = 500,
            description = "AI 프로필 생성"
        )

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(transactionRepository.save(any<GoldTransaction>())).thenAnswer { invocation ->
            val transaction = invocation.getArgument<GoldTransaction>(0)
            GoldTransaction(
                id = 1L,
                user = transaction.user,
                type = transaction.type,
                amount = transaction.amount,
                balanceAfter = transaction.balanceAfter,
                description = transaction.description,
                status = TransactionStatus.COMPLETED
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = goldService.spendGold(1L, request)

        // Then
        assertNotNull(result)
        assertEquals(-500, result.amount)  // Spend amounts are negative
        assertEquals(TransactionType.SPEND, result.type)
        assertEquals(500, testUser.goldBalance)  // 1000 - 500
    }

    @Test
    fun `spendGold should throw exception when insufficient balance`() {
        // Given
        val request = SpendRequest(
            amount = 2000,  // More than balance
            description = "AI 프로필 생성"
        )
        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))

        // When & Then
        assertThrows<BadRequestException> {
            goldService.spendGold(1L, request)
        }
    }

    @Test
    fun `spendGold should throw exception when amount is not positive`() {
        // Given
        val request = SpendRequest(amount = -100, description = "test")
        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))

        // When & Then
        assertThrows<BadRequestException> {
            goldService.spendGold(1L, request)
        }
    }

    @Test
    fun `grantReward should add gold as reward`() {
        // Given
        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(transactionRepository.save(any<GoldTransaction>())).thenAnswer { invocation ->
            val transaction = invocation.getArgument<GoldTransaction>(0)
            GoldTransaction(
                id = 1L,
                user = transaction.user,
                type = transaction.type,
                amount = transaction.amount,
                balanceAfter = transaction.balanceAfter,
                description = transaction.description,
                status = TransactionStatus.COMPLETED
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = goldService.grantReward(1L, 50, "일일 출석 보상")

        // Then
        assertNotNull(result)
        assertEquals(50, result.amount)
        assertEquals(TransactionType.REWARD, result.type)
        assertEquals(1050, testUser.goldBalance)  // 1000 + 50
    }

    @Test
    fun `grantReward should throw exception when user not found`() {
        // Given
        whenever(userRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            goldService.grantReward(999L, 50, "보상")
        }
    }

    @Test
    fun `getTransactions should return paginated transaction history`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val transaction1 = GoldTransaction(
            id = 1L,
            user = testUser,
            type = TransactionType.CHARGE,
            amount = 100,
            balanceAfter = 1100,
            description = "골드 충전",
            status = TransactionStatus.COMPLETED
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        val transaction2 = GoldTransaction(
            id = 2L,
            user = testUser,
            type = TransactionType.SPEND,
            amount = -50,
            balanceAfter = 1050,
            description = "AI 프로필",
            status = TransactionStatus.COMPLETED
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        val transactions = listOf(transaction1, transaction2)
        val page = PageImpl(transactions, pageable, 2)

        whenever(transactionRepository.findAllByUserIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(page)

        // When
        val result = goldService.getTransactions(1L, pageable)

        // Then
        assertEquals(2, result.content.size)
        assertEquals(100, result.content[0].amount)
        assertEquals(-50, result.content[1].amount)
    }

    // === Refund Tests ===

    private fun createSpendTransaction(id: Long, user: User, amount: Int): GoldTransaction {
        return GoldTransaction(
            id = id,
            user = user,
            type = TransactionType.SPEND,
            amount = -amount,
            balanceAfter = user.goldBalance - amount,
            description = "테스트 사용",
            status = TransactionStatus.COMPLETED
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
    }

    @Test
    fun `refundTransaction should refund SPEND transaction successfully`() {
        // Given
        val spendTx = createSpendTransaction(10L, testUser, 500)
        val request = RefundRequest(transactionId = 10L, reason = "테스트 환불")

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(transactionRepository.findById(10L)).thenReturn(Optional.of(spendTx))
        whenever(transactionRepository.existsByOriginalTransactionId(10L)).thenReturn(false)
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(transactionRepository.save(any<GoldTransaction>())).thenAnswer { invocation ->
            val tx = invocation.getArgument<GoldTransaction>(0)
            GoldTransaction(
                id = 100L,
                user = tx.user,
                type = tx.type,
                amount = tx.amount,
                balanceAfter = tx.balanceAfter,
                description = tx.description,
                originalTransactionId = tx.originalTransactionId,
                status = TransactionStatus.COMPLETED
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = goldService.refundTransaction(1L, request)

        // Then
        assertEquals(100L, result.refundTransactionId)
        assertEquals(10L, result.originalTransactionId)
        assertEquals(500, result.refundedAmount)
        assertEquals(1500, testUser.goldBalance) // 1000 + 500
    }

    @Test
    fun `refundTransaction should throw when transaction not found`() {
        // Given
        val request = RefundRequest(transactionId = 999L)
        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(transactionRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        val ex = assertThrows<NotFoundException> {
            goldService.refundTransaction(1L, request)
        }
        assertEquals("Transaction not found", ex.message)
    }

    @Test
    fun `refundTransaction should throw when transaction is not SPEND type`() {
        // Given
        val chargeTx = GoldTransaction(
            id = 10L,
            user = testUser,
            type = TransactionType.CHARGE,
            amount = 500,
            balanceAfter = 1500,
            description = "충전",
            status = TransactionStatus.COMPLETED
        )
        val request = RefundRequest(transactionId = 10L)

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(transactionRepository.findById(10L)).thenReturn(Optional.of(chargeTx))

        // When & Then
        val ex = assertThrows<BadRequestException> {
            goldService.refundTransaction(1L, request)
        }
        assertEquals("Only SPEND transactions can be refunded", ex.message)
    }

    @Test
    fun `refundTransaction should throw when already refunded`() {
        // Given
        val spendTx = createSpendTransaction(10L, testUser, 500)
        val request = RefundRequest(transactionId = 10L)

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(transactionRepository.findById(10L)).thenReturn(Optional.of(spendTx))
        whenever(transactionRepository.existsByOriginalTransactionId(10L)).thenReturn(true)

        // When & Then
        val ex = assertThrows<ConflictException> {
            goldService.refundTransaction(1L, request)
        }
        assertEquals("Transaction already refunded", ex.message)
    }

    @Test
    fun `refundTransaction should throw when not user's transaction`() {
        // Given
        val otherUser = createTestUser(2L, "other", "다른유저")
        val spendTx = createSpendTransaction(10L, otherUser, 500)
        val request = RefundRequest(transactionId = 10L)

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(transactionRepository.findById(10L)).thenReturn(Optional.of(spendTx))
        whenever(transactionRepository.existsByOriginalTransactionId(10L)).thenReturn(false)

        // When & Then
        val ex = assertThrows<ForbiddenException> {
            goldService.refundTransaction(1L, request)
        }
        assertEquals("Cannot refund another user's transaction", ex.message)
    }

    // === systemRefund Tests ===

    @Test
    fun `systemRefund should add gold and create REFUND transaction`() {
        // Given
        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(transactionRepository.save(any<GoldTransaction>())).thenAnswer { invocation ->
            val transaction = invocation.getArgument<GoldTransaction>(0)
            GoldTransaction(
                id = 1L,
                user = transaction.user,
                type = transaction.type,
                amount = transaction.amount,
                balanceAfter = transaction.balanceAfter,
                description = transaction.description,
                referenceType = transaction.referenceType,
                referenceId = transaction.referenceId,
                status = TransactionStatus.COMPLETED
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = goldService.systemRefund(1L, 50, "AI 프로필 취소 환불", "AI_PROFILE_CANCEL", 10L)

        // Then
        assertNotNull(result)
        assertEquals(50, result.amount)
        assertEquals(TransactionType.REFUND, result.type)
        assertEquals(1050, testUser.goldBalance) // 1000 + 50
    }

    @Test
    fun `systemRefund should throw when user not found`() {
        // Given
        whenever(userRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            goldService.systemRefund(999L, 50, "환불")
        }
    }

    @Test
    fun `systemRefund should work without reference fields`() {
        // Given
        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(transactionRepository.save(any<GoldTransaction>())).thenAnswer { invocation ->
            val transaction = invocation.getArgument<GoldTransaction>(0)
            GoldTransaction(
                id = 1L,
                user = transaction.user,
                type = transaction.type,
                amount = transaction.amount,
                balanceAfter = transaction.balanceAfter,
                description = transaction.description,
                status = TransactionStatus.COMPLETED
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = goldService.systemRefund(1L, 100, "시스템 환불")

        // Then
        assertEquals(TransactionType.REFUND, result.type)
        assertEquals(100, result.amount)
        assertEquals(1100, testUser.goldBalance) // 1000 + 100
    }

    // === chargeGold bonus Tests ===

    @Test
    fun `chargeGold should add bonus to total amount`() {
        // Given
        val request = ChargeRequest(
            amount = 100,
            bonus = 20,
            paymentMethod = "CARD"
        )

        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(transactionRepository.save(any<GoldTransaction>())).thenAnswer { invocation ->
            val transaction = invocation.getArgument<GoldTransaction>(0)
            GoldTransaction(
                id = 1L,
                user = transaction.user,
                type = transaction.type,
                amount = transaction.amount,
                balanceAfter = transaction.balanceAfter,
                description = transaction.description,
                paymentMethod = transaction.paymentMethod,
                status = TransactionStatus.COMPLETED
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = goldService.chargeGold(1L, request)

        // Then
        assertEquals(120, result.amount) // 100 + 20 bonus
        assertEquals(1120, testUser.goldBalance) // 1000 + 120
    }

    // === grantWelcomeGoldIfNeeded Tests ===

    @Test
    fun `grantWelcomeGoldIfNeeded should grant gold and create SIGNUP_WELCOME transaction when none exists`() {
        // Given
        whenever(systemSettingService.getInt(GoldService.SETTING_WELCOME_GOLD, 30)).thenReturn(30)
        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(transactionRepository.existsByUserIdAndReferenceType(1L, GoldService.REF_TYPE_SIGNUP_WELCOME)).thenReturn(false)
        whenever(userRepository.save(any<User>())).thenReturn(testUser)
        whenever(transactionRepository.save(any<GoldTransaction>())).thenAnswer { invocation ->
            val transaction = invocation.getArgument<GoldTransaction>(0)
            GoldTransaction(
                id = 1L,
                user = transaction.user,
                type = transaction.type,
                amount = transaction.amount,
                balanceAfter = transaction.balanceAfter,
                description = transaction.description,
                referenceType = transaction.referenceType,
                status = TransactionStatus.COMPLETED
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = goldService.grantWelcomeGoldIfNeeded(1L)

        // Then
        assertNotNull(result)
        assertEquals(30, result!!.amount)
        assertEquals(TransactionType.REWARD, result.type)
        assertEquals(1030, testUser.goldBalance) // 1000 + 30

        val captor = org.mockito.ArgumentCaptor.forClass(GoldTransaction::class.java)
        verify(transactionRepository).save(captor.capture())
        assertEquals(GoldService.REF_TYPE_SIGNUP_WELCOME, captor.value.referenceType)
    }

    @Test
    fun `grantWelcomeGoldIfNeeded should return null and skip grant when SIGNUP_WELCOME already exists`() {
        // Given
        whenever(systemSettingService.getInt(GoldService.SETTING_WELCOME_GOLD, 30)).thenReturn(30)
        whenever(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser))
        whenever(transactionRepository.existsByUserIdAndReferenceType(1L, GoldService.REF_TYPE_SIGNUP_WELCOME)).thenReturn(true)

        // When
        val result = goldService.grantWelcomeGoldIfNeeded(1L)

        // Then
        assertNull(result)
        assertEquals(1000, testUser.goldBalance) // unchanged
        verify(transactionRepository, never()).save(any<GoldTransaction>())
    }

    @Test
    fun `grantWelcomeGoldIfNeeded should skip grant when signup welcome gold setting is not positive`() {
        // Given
        whenever(systemSettingService.getInt(GoldService.SETTING_WELCOME_GOLD, 30)).thenReturn(0)

        // When
        val result = goldService.grantWelcomeGoldIfNeeded(1L)

        // Then
        assertNull(result)
        verify(userRepository, never()).findByIdForUpdate(any())
    }
}
