package com.goldpet.domain.gold.dto

import com.goldpet.domain.gold.entity.GoldTransaction
import com.goldpet.domain.gold.entity.TransactionStatus
import com.goldpet.domain.gold.entity.TransactionType
import java.time.LocalDateTime

data class GoldBalanceResponse(
    val balance: Int,
    val totalCharged: Int,
    val totalSpent: Int
)

data class ChargeRequest(
    val amount: Int,
    /** 보너스 골드량. 총 지급량 = amount + bonus. 현재 description으로만 추적되며, 추후 별도 컬럼 추가 가능. */
    val bonus: Int = 0,
    val paymentMethod: String,
    val externalTransactionId: String? = null
)

data class SpendRequest(
    val amount: Int,
    val description: String,
    val referenceType: String? = null,
    val referenceId: Long? = null
)

data class TransactionResponse(
    val id: Long,
    val type: TransactionType,
    val amount: Int,
    val balanceAfter: Int,
    val description: String?,
    val status: TransactionStatus,
    val createdAt: LocalDateTime?
) {
    companion object {
        fun from(t: GoldTransaction) = TransactionResponse(
            id = t.id,
            type = t.type,
            amount = t.amount,
            balanceAfter = t.balanceAfter,
            description = t.description,
            status = t.status,
            createdAt = t.createdAt
        )
    }
}

data class GoldProductResponse(
    val id: String,
    val name: String,
    val goldAmount: Int,
    val price: Int,
    val bonus: Int = 0,
    val productType: String = "ONE_TIME",
    val durationMonths: Int? = null,
    val discountPercent: Int = 0,
    val monthlyPrice: Int? = null
)

data class RefundRequest(
    val transactionId: Long,
    val reason: String? = null
)

data class RefundResponse(
    val refundTransactionId: Long,
    val originalTransactionId: Long,
    val refundedAmount: Int,
    val balanceAfter: Int
)
