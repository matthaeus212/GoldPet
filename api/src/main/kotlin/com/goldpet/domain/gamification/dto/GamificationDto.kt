package com.goldpet.domain.gamification.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.UserBadge
import java.time.LocalDateTime

data class BadgeResponse(
    val id: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val conditionType: String? = null,
    val conditionValue: Int? = null,
    val rewardGold: Int? = null,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    @get:JsonProperty("isRepeatable")
    val isRepeatable: Boolean = false,
    val repeatCycle: String? = null,
    val acquiredAt: LocalDateTime? = null
) {
    companion object {
        fun from(badge: Badge): BadgeResponse {
            return BadgeResponse(
                id = badge.id,
                name = badge.name,
                description = badge.description,
                imageUrl = badge.imageUrl,
                conditionType = badge.conditionType?.name,
                conditionValue = badge.conditionValue,
                rewardGold = badge.rewardGold,
                startDate = badge.startDate,
                endDate = badge.endDate,
                isRepeatable = badge.isRepeatable,
                repeatCycle = badge.repeatCycle
            )
        }

        fun from(userBadge: UserBadge): BadgeResponse {
            return BadgeResponse(
                id = userBadge.badge.id,
                name = userBadge.badge.name,
                description = userBadge.badge.description,
                imageUrl = userBadge.badge.imageUrl,
                conditionType = userBadge.badge.conditionType?.name,
                conditionValue = userBadge.badge.conditionValue,
                rewardGold = userBadge.badge.rewardGold,
                startDate = userBadge.badge.startDate,
                endDate = userBadge.badge.endDate,
                isRepeatable = userBadge.badge.isRepeatable,
                repeatCycle = userBadge.badge.repeatCycle,
                acquiredAt = userBadge.createdAt
            )
        }
    }
}
