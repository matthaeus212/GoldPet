package com.goldpet.domain.friend.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class LikeResponse(
    val likeId: Long,
    val toUserId: Long,
    @get:JsonProperty("isMutual")
    val isMutual: Boolean
)
