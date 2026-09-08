package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.EconomyStatsResponse
import com.goldpet.domain.admin.controller.GoldAdjustRequest
import com.goldpet.domain.admin.controller.GoldProductAdminResponse
import com.goldpet.domain.admin.controller.GoldProductCreateRequest
import com.goldpet.domain.admin.controller.GoldProductUpdateRequest
import com.goldpet.domain.admin.controller.AdminRefundRequest
import com.goldpet.domain.admin.controller.TransactionAdminResponse
import com.goldpet.domain.gold.entity.GoldProduct
import com.goldpet.domain.gold.entity.GoldTransaction
import com.goldpet.domain.gold.entity.TransactionStatus
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.repository.GoldProductRepository
import com.goldpet.domain.gold.repository.GoldTransactionRepository
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class AdminEconomyService(
    private val userRepository: UserRepository,
    private val goldTransactionRepository: GoldTransactionRepository,
    private val goldProductRepository: GoldProductRepository
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    @Transactional(readOnly = true)
    fun getStats(): EconomyStatsResponse {
        val totalGold = userRepository.sumGoldBalance()
        val today = LocalDate.now().atStartOfDay()

        return EconomyStatsResponse(
            totalGoldCirculation = totalGold,
            totalRevenue = goldTransactionRepository.sumAmountByType(TransactionType.CHARGE),
            todayRevenue = goldTransactionRepository.sumAmountByTypeSince(TransactionType.CHARGE, today),
            activeSubscriptions = 0,
            pendingPayouts = 0
        )
    }

    fun getTransactions(type: String?, pageable: Pageable): Page<TransactionAdminResponse> {
        val transactionType = type?.let {
            try { TransactionType.valueOf(it) } catch (e: Exception) { null }
        }
        return goldTransactionRepository.findAllForAdmin(transactionType, pageable).map { toTransactionResponse(it) }
    }

    fun getUserTransactions(userId: Long, pageable: Pageable): Page<TransactionAdminResponse> {
        return goldTransactionRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map { toTransactionResponse(it) }
    }

    private fun toTransactionResponse(t: GoldTransaction): TransactionAdminResponse =
        TransactionAdminResponse(
            id = t.id,
            userId = t.user.id,
            userNickname = t.user.nickname ?: "사용자",
            type = t.type.name,
            amount = t.amount,
            description = t.description ?: "",
            createdAt = t.createdAt.format(formatter)
        )

    @Transactional
    fun adjustGold(request: GoldAdjustRequest) {
        val user = userRepository.findByIdForUpdate(request.userId)
            .orElseThrow { NotFoundException("User not found: ${request.userId}") }

        if (user.goldBalance + request.amount < 0) {
            throw BadRequestException("Balance cannot go below zero (current: ${user.goldBalance}, adjust: ${request.amount})")
        }
        user.goldBalance += request.amount
        userRepository.save(user)

        goldTransactionRepository.save(GoldTransaction(
            user = user,
            type = TransactionType.ADMIN_ADJUST,
            amount = request.amount,
            balanceAfter = user.goldBalance,
            description = request.reason
        ))
    }

    fun getProducts(): List<GoldProductAdminResponse> {
        return goldProductRepository.findAll().map { p ->
            GoldProductAdminResponse(
                id = p.id,
                productCode = p.productCode,
                name = p.name,
                goldAmount = p.goldAmount,
                price = p.price,
                bonus = p.bonus,
                isActive = p.isActive,
                displayOrder = p.displayOrder
            )
        }
    }

    @Transactional
    @CacheEvict(value = ["gold:products"], allEntries = true)
    fun createProduct(request: GoldProductCreateRequest): GoldProductAdminResponse {
        if (goldProductRepository.findByProductCode(request.productCode) != null) {
            throw ConflictException("Product code already exists: ${request.productCode}")
        }

        val product = goldProductRepository.save(GoldProduct(
            productCode = request.productCode,
            name = request.name,
            goldAmount = request.goldAmount,
            price = request.price,
            bonus = request.bonus,
            displayOrder = request.displayOrder
        ))

        return GoldProductAdminResponse(
            id = product.id,
            productCode = product.productCode,
            name = product.name,
            goldAmount = product.goldAmount,
            price = product.price,
            bonus = product.bonus,
            isActive = product.isActive,
            displayOrder = product.displayOrder
        )
    }

    @Transactional
    @CacheEvict(value = ["gold:products"], allEntries = true)
    fun updateProduct(id: Long, request: GoldProductUpdateRequest): GoldProductAdminResponse {
        val product = goldProductRepository.findById(id)
            .orElseThrow { NotFoundException("Product not found: $id") }

        request.name?.let { product.name = it }
        request.goldAmount?.let { product.goldAmount = it }
        request.price?.let { product.price = it }
        request.bonus?.let { product.bonus = it }
        request.isActive?.let { product.isActive = it }
        request.displayOrder?.let { product.displayOrder = it }

        val saved = goldProductRepository.save(product)

        return GoldProductAdminResponse(
            id = saved.id,
            productCode = saved.productCode,
            name = saved.name,
            goldAmount = saved.goldAmount,
            price = saved.price,
            bonus = saved.bonus,
            isActive = saved.isActive,
            displayOrder = saved.displayOrder
        )
    }

    @Transactional
    @CacheEvict(value = ["gold:products"], allEntries = true)
    fun deleteProduct(id: Long) {
        val product = goldProductRepository.findById(id)
            .orElseThrow { NotFoundException("Product not found: $id") }
        product.isActive = false
        goldProductRepository.save(product)
    }

    @Transactional
    fun refundTransaction(request: AdminRefundRequest) {
        val originalTransaction = goldTransactionRepository.findById(request.transactionId)
            .orElseThrow { NotFoundException("Transaction not found") }

        if (originalTransaction.type != TransactionType.SPEND) {
            throw BadRequestException("Only SPEND transactions can be refunded")
        }

        if (goldTransactionRepository.existsByOriginalTransactionId(request.transactionId)) {
            throw ConflictException("Transaction already refunded")
        }

        val user = userRepository.findByIdForUpdate(originalTransaction.user.id)
            .orElseThrow { NotFoundException("User not found") }

        val refundAmount = kotlin.math.abs(originalTransaction.amount)
        user.goldBalance += refundAmount
        userRepository.save(user)

        goldTransactionRepository.save(GoldTransaction(
            user = user,
            type = TransactionType.REFUND,
            amount = refundAmount,
            balanceAfter = user.goldBalance,
            description = "관리자 환불: ${request.reason}",
            originalTransactionId = originalTransaction.id,
            status = TransactionStatus.COMPLETED
        ))
    }
}
