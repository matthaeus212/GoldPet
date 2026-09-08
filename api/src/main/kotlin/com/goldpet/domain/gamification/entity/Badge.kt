package com.goldpet.domain.gamification.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "badges")
class Badge(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String,

    @Column(nullable = false)
    var imageUrl: String,

    @Column(name = "condition_type")
    @Enumerated(EnumType.STRING)
    var conditionType: BadgeConditionType? = null,

    @Column(name = "condition_value")
    var conditionValue: Int? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "reward_gold")
    var rewardGold: Int? = null,

    @Column(name = "start_date")
    var startDate: LocalDateTime? = null,

    @Column(name = "end_date")
    var endDate: LocalDateTime? = null,

    @Column(name = "is_repeatable", nullable = false)
    var isRepeatable: Boolean = false,

    @Column(name = "repeat_cycle")
    var repeatCycle: String? = null
)
