package com.goldpet.domain.gamification.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.time.LocalDateTime

data class DailyMissionResponse(
    val missionDate: LocalDate,
    val missions: List<MissionItem>
)

data class MissionItem(
    val badgeId: Long,
    val name: String,
    val description: String,
    val imageUrl: String,
    val conditionType: String? = null,
    val conditionValue: Int? = null,
    val rewardGold: Int? = null,
    /** 현재 진행값(user_badges 읽기 조인). 미완료 시 null. */
    val currentValue: Int? = null,
    @get:JsonProperty("completed")
    val completed: Boolean = false,
    val completedAt: LocalDateTime? = null
)
