package com.goldpet.domain.admin.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "admin_audit_logs")
class AdminAuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "admin_user_id", nullable = false)
    val adminUserId: Long,

    @Column(nullable = false)
    val action: String, // VIEW, UPDATE, DELETE, EXPORT, LOGIN

    @Column(name = "target_type")
    val targetType: String? = null, // USER, POST, COMMENT, CHAT, SETTING

    @Column(name = "target_id")
    val targetId: Long? = null,

    @Column(name = "ip_address")
    val ipAddress: String? = null,

    @Column(name = "request_path")
    val requestPath: String? = null,

    @Column(name = "request_method")
    val requestMethod: String? = null,

    @Column(name = "response_status")
    val responseStatus: Int? = null,

    @Column(columnDefinition = "TEXT")
    val details: String? = null,

    /** V70 — Sprint 2 BLOCKER #7. 운영자 브라우저/CLI 식별. */
    @Column(name = "user_agent", length = 500)
    val userAgent: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
