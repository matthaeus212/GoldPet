package com.goldpet.domain.admin.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "push_delivery_logs")
class PushDeliveryLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    val template: NotificationTemplate? = null,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, length = 1000)
    val message: String,

    @Column(name = "target_type", nullable = false, length = 50)
    val targetType: String,  // ALL, SEGMENT, USER

    @Column(name = "target_value", length = 500)
    val targetValue: String? = null,

    @Column(name = "target_count", nullable = false)
    val targetCount: Int = 0,

    @Column(name = "delivered_count", nullable = false)
    var deliveredCount: Int = 0,

    @Column(name = "failed_count", nullable = false)
    var failedCount: Int = 0,

    @Column(name = "scheduled_at")
    val scheduledAt: LocalDateTime? = null,

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null,

    @Column(nullable = false, length = 50)
    var status: String = "PENDING",  // PENDING, SENDING, COMPLETED, FAILED

    @Column(name = "sent_by", nullable = false, length = 100)
    val sentBy: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
