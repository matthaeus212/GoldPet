package com.goldpet.domain.notification.reengagement.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 재참여 넛지 일일 dedup row (W2c, plan §2.4).
 * `(user_id, send_date)` UNIQUE = 하루 1회 캡 + 배포 재시작 멱등(`reengage:{userId}:{date}`).
 */
@Entity
@Table(
    name = "reengagement_sends",
    uniqueConstraints = [UniqueConstraint(name = "uq_reengagement_user_date", columnNames = ["user_id", "send_date"])]
)
@EntityListeners(AuditingEntityListener::class)
class ReengagementSend(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "send_date", nullable = false)
    val sendDate: LocalDate,

    @Column(name = "nudge_type", nullable = false, length = 32)
    val nudgeType: String,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
