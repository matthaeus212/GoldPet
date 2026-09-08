package com.goldpet.domain.gold.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

enum class IdempotencyStatus { PROCESSING, COMPLETED, FAILED }

@Entity
@Table(name = "gold_idempotency_keys")
class GoldIdempotencyKey(
    @Id
    @Column(name = "idempotency_key", length = 64)
    val idempotencyKey: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "request_hash", nullable = false, length = 128)
    val requestHash: String,

    @Column(name = "transaction_id")
    var transactionId: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    var responseBody: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: IdempotencyStatus = IdempotencyStatus.PROCESSING,

    @Column(name = "http_status")
    var httpStatus: Short? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now().plusDays(7),
)
