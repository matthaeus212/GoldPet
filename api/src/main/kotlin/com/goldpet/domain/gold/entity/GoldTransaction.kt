package com.goldpet.domain.gold.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*

enum class TransactionType {
    CHARGE,         // 충전
    SPEND,          // 사용
    REWARD,         // 보상
    REFUND,         // 환불
    ADMIN_ADJUST    // 관리자 수동 조정
}

enum class TransactionStatus {
    PENDING,    // 대기
    COMPLETED,  // 완료
    FAILED,     // 실패
    CANCELLED   // 취소
}

@Entity
@Table(name = "gold_transactions")
class GoldTransaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: TransactionType,

    @Column(nullable = false)
    val amount: Int,

    @Column(name = "balance_after", nullable = false)
    val balanceAfter: Int,

    @Column(name = "description")
    val description: String? = null,

    @Column(name = "reference_type")
    val referenceType: String? = null,

    @Column(name = "reference_id")
    val referenceId: Long? = null,

    @Column(name = "original_transaction_id")
    val originalTransactionId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TransactionStatus = TransactionStatus.COMPLETED,

    @Column(name = "payment_method")
    val paymentMethod: String? = null,

    @Column(name = "external_transaction_id")
    val externalTransactionId: String? = null

) : BaseTimeEntity()
