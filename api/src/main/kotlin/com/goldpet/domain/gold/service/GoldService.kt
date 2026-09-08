package com.goldpet.domain.gold.service

import com.goldpet.domain.common.exception.*
import com.goldpet.domain.gold.dto.*
import com.goldpet.domain.gold.entity.GoldTransaction
import com.goldpet.domain.gold.entity.TransactionStatus
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.repository.GoldProductRepository
import com.goldpet.domain.gold.repository.GoldTransactionRepository
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class GoldService(
    private val transactionRepository: GoldTransactionRepository,
    private val userRepository: UserRepository,
    private val productRepository: GoldProductRepository,
    private val systemSettingService: SystemSettingService
) {

    companion object {
        const val SETTING_WELCOME_GOLD = "signup.welcome.gold"
        const val REF_TYPE_SIGNUP_WELCOME = "SIGNUP_WELCOME"
    }

    fun getBalance(userId: Long): GoldBalanceResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found") }

        val oneMonthAgo = LocalDateTime.now().minusMonths(1)
        val totalCharged = transactionRepository.sumAmountByUserAndTypeSince(userId, TransactionType.CHARGE, oneMonthAgo) ?: 0
        val totalSpent = transactionRepository.sumAmountByUserAndTypeSince(userId, TransactionType.SPEND, oneMonthAgo) ?: 0

        return GoldBalanceResponse(
            balance = user.goldBalance,
            totalCharged = totalCharged,
            totalSpent = kotlin.math.abs(totalSpent)
        )
    }

    @Cacheable("gold:products")
    fun getProducts(): List<GoldProductResponse> {
        return productRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()
            .map { GoldProductResponse(
                id = it.productCode,
                name = it.name,
                goldAmount = it.goldAmount,
                price = it.price,
                bonus = it.bonus,
                productType = it.productType,
                durationMonths = it.durationMonths,
                discountPercent = it.discountPercent,
                monthlyPrice = it.monthlyPrice
            ) }
    }

    fun getTransactions(userId: Long, pageable: Pageable, type: TransactionType? = null): Page<TransactionResponse> {
        val page = if (type != null) {
            transactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable)
        } else {
            transactionRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
        }
        return page.map { TransactionResponse.from(it) }
    }

    fun getTotalRewardAmount(userId: Long): Int {
        return transactionRepository.sumAmountByUserAndTypeSince(
            userId, TransactionType.REWARD, LocalDateTime.of(2020, 1, 1, 0, 0)
        ) ?: 0
    }

    @Transactional
    fun chargeGold(userId: Long, request: ChargeRequest): TransactionResponse {
        val user = userRepository.findByIdForUpdate(userId)
            .orElseThrow { NotFoundException("User not found") }

        if (request.amount <= 0) {
            throw BadRequestException("Amount must be positive")
        }

        // 총 지급량 = 결제 골드 + 보너스. 보너스는 description에 기록 (추후 별도 컬럼 추가 가능)
        val totalAmount = request.amount + request.bonus
        user.goldBalance += totalAmount
        userRepository.save(user)

        val transaction = GoldTransaction(
            user = user,
            type = TransactionType.CHARGE,
            amount = totalAmount,
            balanceAfter = user.goldBalance,
            description = if (request.bonus > 0) "골드 충전 (보너스 +${request.bonus})" else "골드 충전",
            paymentMethod = request.paymentMethod,
            externalTransactionId = request.externalTransactionId,
            status = TransactionStatus.COMPLETED
        )

        val saved = transactionRepository.save(transaction)
        return TransactionResponse.from(saved)
    }

    @Transactional
    fun spendGold(userId: Long, request: SpendRequest): TransactionResponse {
        val user = userRepository.findByIdForUpdate(userId)
            .orElseThrow { NotFoundException("User not found") }

        if (request.amount <= 0) {
            throw BadRequestException("Amount must be positive")
        }

        if (user.goldBalance < request.amount) {
            throw BadRequestException("골드 잔액이 부족합니다.")
        }

        user.goldBalance -= request.amount
        userRepository.save(user)

        val transaction = GoldTransaction(
            user = user,
            type = TransactionType.SPEND,
            amount = -request.amount,
            balanceAfter = user.goldBalance,
            description = request.description,
            referenceType = request.referenceType,
            referenceId = request.referenceId,
            status = TransactionStatus.COMPLETED
        )

        val saved = transactionRepository.save(transaction)
        return TransactionResponse.from(saved)
    }

    @Transactional
    fun grantReward(userId: Long, amount: Int, description: String): TransactionResponse {
        val user = userRepository.findByIdForUpdate(userId)
            .orElseThrow { NotFoundException("User not found") }

        user.goldBalance += amount
        userRepository.save(user)

        val transaction = GoldTransaction(
            user = user,
            type = TransactionType.REWARD,
            amount = amount,
            balanceAfter = user.goldBalance,
            description = description,
            status = TransactionStatus.COMPLETED
        )

        val saved = transactionRepository.save(transaction)
        return TransactionResponse.from(saved)
    }

    /**
     * 회원가입 축하 골드 1회 지급. referenceType=SIGNUP_WELCOME 이력이 있으면 건너뛴다.
     * 신규 가입(AuthService)과 기존 회원 소급(WelcomeGoldBackfillRunner) 양쪽에서 사용.
     * 지급액은 signup.welcome.gold 설정, 0 이하면 지급하지 않는다.
     */
    @Transactional
    fun grantWelcomeGoldIfNeeded(userId: Long): TransactionResponse? {
        val amount = systemSettingService.getInt(SETTING_WELCOME_GOLD, 30)
        if (amount <= 0) return null

        val user = userRepository.findByIdForUpdate(userId)
            .orElseThrow { NotFoundException("User not found") }
        if (transactionRepository.existsByUserIdAndReferenceType(userId, REF_TYPE_SIGNUP_WELCOME)) {
            return null
        }

        user.goldBalance += amount
        userRepository.save(user)

        val transaction = GoldTransaction(
            user = user,
            type = TransactionType.REWARD,
            amount = amount,
            balanceAfter = user.goldBalance,
            description = "회원가입 축하 골드",
            referenceType = REF_TYPE_SIGNUP_WELCOME,
            status = TransactionStatus.COMPLETED
        )
        return TransactionResponse.from(transactionRepository.save(transaction))
    }

    /**
     * 시스템에서 자동으로 환불 처리하는 메서드.
     *
     * [refundTransaction]과의 차이:
     * - refundTransaction(): 사용자가 기존 SPEND 거래를 참조하여 환불 요청 (originalTransactionId 필수)
     * - systemRefund(): 시스템이 서비스 취소/실패 등으로 자동 환불 (originalTransactionId 없음, referenceType/Id로 추적)
     */
    @Transactional
    fun systemRefund(userId: Long, amount: Int, description: String, referenceType: String? = null, referenceId: Long? = null): TransactionResponse {
        val user = userRepository.findByIdForUpdate(userId)
            .orElseThrow { NotFoundException("User not found") }

        user.goldBalance += amount
        userRepository.save(user)

        val transaction = GoldTransaction(
            user = user,
            type = TransactionType.REFUND,
            amount = amount,
            balanceAfter = user.goldBalance,
            description = description,
            referenceType = referenceType,
            referenceId = referenceId,
            status = TransactionStatus.COMPLETED
        )

        val saved = transactionRepository.save(transaction)
        return TransactionResponse.from(saved)
    }

    @Transactional
    fun refundTransaction(userId: Long, request: RefundRequest): RefundResponse {
        val user = userRepository.findByIdForUpdate(userId)
            .orElseThrow { NotFoundException("User not found") }

        val originalTransaction = transactionRepository.findById(request.transactionId)
            .orElseThrow { NotFoundException("Transaction not found") }

        if (originalTransaction.user.id != userId) {
            throw ForbiddenException("Cannot refund another user's transaction")
        }

        if (originalTransaction.type != TransactionType.SPEND) {
            throw BadRequestException("Only SPEND transactions can be refunded")
        }

        if (transactionRepository.existsByOriginalTransactionId(request.transactionId)) {
            throw ConflictException("Transaction already refunded")
        }

        if (originalTransaction.status != TransactionStatus.COMPLETED) {
            throw BadRequestException("Only completed transactions can be refunded")
        }

        val refundAmount = kotlin.math.abs(originalTransaction.amount)
        user.goldBalance += refundAmount
        userRepository.save(user)

        val refundTransaction = GoldTransaction(
            user = user,
            type = TransactionType.REFUND,
            amount = refundAmount,
            balanceAfter = user.goldBalance,
            description = request.reason ?: "거래 환불 (원본 #${originalTransaction.id})",
            originalTransactionId = originalTransaction.id,
            status = TransactionStatus.COMPLETED
        )

        val saved = transactionRepository.save(refundTransaction)

        return RefundResponse(
            refundTransactionId = saved.id,
            originalTransactionId = originalTransaction.id,
            refundedAmount = refundAmount,
            balanceAfter = user.goldBalance
        )
    }
}
