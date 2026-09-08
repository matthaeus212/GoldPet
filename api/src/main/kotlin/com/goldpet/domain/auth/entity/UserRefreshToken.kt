package com.goldpet.domain.auth.entity

import jakarta.persistence.*
import java.time.LocalDateTime

enum class RefreshTokenRevokeReason {
    ROTATED,           // 정상 회전 — 새 token 발급으로 인한 parent revoke
    REUSE_DETECTED,    // 이미 revoked된 token 재사용 시도 → 해당 device chain 전체 revoke
    ADMIN_FORCE,       // Admin 강제 로그아웃
    LOGOUT,            // 사용자 명시적 로그아웃
    EXPIRED,           // TTL 초과 (cleanup job)
}

@Entity
@Table(name = "user_refresh_tokens")
class UserRefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "device_id", nullable = false, length = 255)
    val deviceId: String,

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    val tokenHash: String,

    @Column(name = "parent_token_hash", length = 128)
    val parentTokenHash: String? = null,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 32)
    var revokeReason: RefreshTokenRevokeReason? = null,

    @Column(name = "user_agent", length = 500)
    val userAgent: String? = null,

    @Column(name = "ip_address", length = 64)
    val ipAddress: String? = null,
)
