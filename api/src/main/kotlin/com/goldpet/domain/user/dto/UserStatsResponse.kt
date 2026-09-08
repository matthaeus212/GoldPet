package com.goldpet.domain.user.dto

data class UserStatsResponse(
    val matchingCount: Long,
    val likesCount: Long,
    val friendsCount: Long
)
