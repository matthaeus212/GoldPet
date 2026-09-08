package com.goldpet.domain.profileboost.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

/**
 * 프로필 노출 부스트 — 고정 윈도우 동안 친구 탐색 랭크/노출을 상승.
 * 골드는 구매 시 GoldService.spendGold()로 차감(이 엔티티는 윈도우만 기록).
 */
@Entity
@Table(name = "profile_boosts")
@EntityListeners(AuditingEntityListener::class)
class ProfileBoost(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "started_at", nullable = false)
    val startedAt: LocalDateTime,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    @Column(name = "gold_cost", nullable = false)
    val goldCost: Int,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun isActiveAt(at: LocalDateTime): Boolean = at.isBefore(expiresAt)
}
