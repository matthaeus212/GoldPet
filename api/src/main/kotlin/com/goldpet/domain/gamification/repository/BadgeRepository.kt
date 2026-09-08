package com.goldpet.domain.gamification.repository

import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.BadgeConditionType
import org.springframework.data.jpa.repository.JpaRepository

interface BadgeRepository : JpaRepository<Badge, Long> {
    fun findAllByConditionTypeAndIsActiveTrue(conditionType: BadgeConditionType): List<Badge>
    fun findAllByIsActiveTrue(): List<Badge>
    fun findAllByIsRepeatableTrueAndRepeatCycleAndIsActiveTrue(repeatCycle: String): List<Badge>
}
