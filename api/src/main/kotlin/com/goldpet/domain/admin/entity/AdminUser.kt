package com.goldpet.domain.admin.entity

import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.config.crypto.EncryptionConverter
import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDateTime

enum class AdminUserRole {
    SUPER_ADMIN,
    OPERATOR,
    VIEWER
}

@Entity
@Table(name = "admin_users")
class AdminUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Convert(converter = EncryptionConverter::class)
    @Column(nullable = false)
    var email: String,

    @Column(name = "email_hash", unique = true)
    var emailHash: String? = null,

    @Column(nullable = false)
    var passwordHash: String,

    @Convert(converter = EncryptionConverter::class)
    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: AdminUserRole = AdminUserRole.OPERATOR,

    // EXT-CDX-005 — TOTP secret 을 평문이 아닌 AES-GCM 으로 저장. EncryptionConverter 는 읽기 시
    // 평문 fallback 을 제공하므로 기존 평문 행도 계속 읽힌다(마이그레이션은 AdminOtpSecretEncryptionBackfillRunner).
    @Convert(converter = EncryptionConverter::class)
    var otpSecret: String? = null,

    /** V69 — TOTP replay 보호. 동일 30s window 코드 재사용 차단. */
    @Column(name = "last_otp_used_at")
    var lastOtpUsedAt: LocalDateTime? = null,

    @Column(nullable = false)
    var isActive: Boolean = true,

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null,

    @Column(name = "must_change_password", nullable = false)
    var mustChangePassword: Boolean = false

) : BaseTimeEntity() {

    /**
     * email 컬럼이 random-IV AES-GCM으로 암호화되어 equality 조회가 불가능하므로
     * 저장/수정 시 email_hash(BlindIndex HMAC-SHA256)를 자동 동기화한다.
     * 호출부가 emailHash를 직접 관리할 필요가 없고 누락 방지.
     */
    @PrePersist
    @PreUpdate
    fun syncEmailHash() {
        emailHash = BlindIndexUtil.hash(email)
    }
}
