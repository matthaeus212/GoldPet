package com.goldpet.domain.user.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "deleted_users")
class DeletedUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    val reason: String? = null,

    @Column(name = "deleted_at", nullable = false)
    val deletedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "data_deletion_scheduled_at", nullable = false)
    val dataDeletionScheduledAt: LocalDateTime = LocalDateTime.now().plusDays(30),

    @Column(name = "deleted_by", nullable = false)
    val deletedBy: String = "SELF"
)
