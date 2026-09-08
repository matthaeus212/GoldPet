package com.goldpet.domain.admin.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "admin_access_logs")
class AdminAccessLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "admin_user_id", nullable = false)
    val adminUserId: Long,

    @Column(length = 50)
    val ipAddress: String?,

    @Column(length = 100)
    val action: String?,

    @Column(length = 255)
    val targetResource: String?,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
