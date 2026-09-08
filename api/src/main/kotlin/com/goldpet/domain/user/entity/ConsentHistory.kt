package com.goldpet.domain.user.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "consent_history")
class ConsentHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false)
    val consentType: ConsentType,

    @Column(name = "consent_version", nullable = false)
    val consentVersion: String = "1.0",

    @Column(name = "is_agreed", nullable = false)
    val isAgreed: Boolean,

    @Column(name = "agreed_at", nullable = false)
    val agreedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "ip_address")
    val ipAddress: String? = null
)

enum class ConsentType {
    TERMS,         // 이용약관
    PRIVACY,       // 개인정보처리방침
    MARKETING,     // 마케팅 동의
    LOCATION       // 위치정보 이용 동의
}
