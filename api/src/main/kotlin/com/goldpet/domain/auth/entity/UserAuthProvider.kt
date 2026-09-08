package com.goldpet.domain.auth.entity

import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user_auth_providers")
class UserAuthProvider(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 50)
    val provider: String,

    @Column(name = "provider_id", nullable = false, length = 255)
    val providerId: String,

    @Column(name = "linked_at", nullable = false)
    val linkedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
)
