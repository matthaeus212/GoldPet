package com.goldpet.domain.profileboost.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldpet.domain.profileboost.entity.ProfileBoost
import java.time.Duration
import java.time.LocalDateTime

data class ProfileBoostResponse(
    val id: Long,
    val startedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val goldCost: Int,
    @get:JsonProperty("active")
    val active: Boolean,
    val remainingSeconds: Long
) {
    companion object {
        fun from(boost: ProfileBoost, now: LocalDateTime): ProfileBoostResponse {
            val remaining = Duration.between(now, boost.expiresAt).seconds.coerceAtLeast(0)
            return ProfileBoostResponse(
                id = boost.id,
                startedAt = boost.startedAt,
                expiresAt = boost.expiresAt,
                goldCost = boost.goldCost,
                active = boost.isActiveAt(now),
                remainingSeconds = remaining
            )
        }
    }
}

/** 현재 활성 부스트 조회 응답 (없으면 active=false). */
data class ActiveBoostResponse(
    @get:JsonProperty("active")
    val active: Boolean,
    val boost: ProfileBoostResponse?
)
