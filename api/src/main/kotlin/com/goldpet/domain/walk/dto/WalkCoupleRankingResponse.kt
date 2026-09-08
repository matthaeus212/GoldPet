package com.goldpet.domain.walk.dto

data class WalkCoupleRankingResponse(
    val yearMonth: String,
    val bestCouple: WalkRankingResponse?,
    val rankings: List<WalkRankingResponse>
)
