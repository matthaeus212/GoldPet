package com.goldpet.domain.walk.dto

data class WalkRankingResponse(
    val rank: Int,
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val petName: String?,
    val petProfileImageUrl: String?,
    val totalDistanceKm: Double,
    val totalMinutes: Long,
    val totalGold: Int,
    val walkCount: Int
)
