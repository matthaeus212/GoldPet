package com.goldpet.domain.auth.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "oauth_nonces")
class OAuthNonce(
    @Id
    @Column(name = "nonce_uuid", columnDefinition = "uuid")
    @JdbcTypeCode(SqlTypes.UUID)
    val nonceUuid: UUID,

    @Column(name = "user_id")
    val userId: Long? = null,

    @Column(name = "provider", nullable = false, length = 32)
    val provider: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
