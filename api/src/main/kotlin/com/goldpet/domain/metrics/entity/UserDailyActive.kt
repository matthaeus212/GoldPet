package com.goldpet.domain.metrics.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Append-only daily-active marker: one row per user per KST day.
 * Rows are written idempotently (INSERT ... ON CONFLICT DO NOTHING); the
 * composite primary key (user_id, active_date) guarantees at most one row.
 */
@Entity
@Table(name = "user_daily_active")
@IdClass(UserDailyActiveId::class)
class UserDailyActive(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Id
    @Column(name = "active_date", nullable = false)
    val activeDate: LocalDate,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/**
 * Composite-id class for [UserDailyActive]. Declared with an explicit no-arg
 * constructor + equals/hashCode so it works regardless of the Kotlin no-arg plugin.
 */
class UserDailyActiveId(
    val userId: Long,
    val activeDate: LocalDate,
) : Serializable {
    constructor() : this(0L, LocalDate.EPOCH)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserDailyActiveId) return false
        return userId == other.userId && activeDate == other.activeDate
    }

    override fun hashCode(): Int = 31 * userId.hashCode() + activeDate.hashCode()

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
