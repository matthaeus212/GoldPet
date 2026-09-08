package com.goldpet.domain.gamification.entity

import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_badges",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "badge_id", "cycle_key"])]
)
@EntityListeners(AuditingEntityListener::class)
class UserBadge(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "badge_id", nullable = false)
    val badge: Badge,

    @Column(name = "current_value", nullable = false)
    var currentValue: Int = 0,

    @Column(name = "cycle_key")
    var cycleKey: String? = null,

    @Column(name = "reward_gold_given", nullable = false)
    var rewardGoldGiven: Int = 0,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
