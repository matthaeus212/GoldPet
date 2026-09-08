// dev-login 접근이 허용된 출발지 IP/CIDR 엔트리
package com.goldpet.domain.auth.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "dev_login_ip_allowlist")
class DevLoginIpAllow(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 단일 IP 또는 CIDR. 예: `115.79.198.72`, `10.1.2.0/24`, `::1` */
    @Column(name = "ip_pattern", nullable = false, length = 64, unique = true)
    var ipPattern: String,

    /** 누구/어디인지. 라벨 없는 IP 는 나중에 지워도 되는지 아무도 모른다. */
    @Column(nullable = false, length = 200)
    var label: String,

    @Column(nullable = false)
    var enabled: Boolean = true,

    /** 임시 QA IP 는 만료를 걸어 방치되지 않게 한다. null = 무기한. */
    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null,

    @Column(name = "created_by")
    var createdBy: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun isActive(now: LocalDateTime = LocalDateTime.now()): Boolean =
        enabled && (expiresAt == null || expiresAt!!.isAfter(now))
}
