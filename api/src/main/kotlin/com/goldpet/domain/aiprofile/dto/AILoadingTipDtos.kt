package com.goldpet.domain.aiprofile.dto

data class AILoadingTipResponse(
    val id: Int,
    val content: String,
    val displayOrder: Int,
    val isActive: Boolean
)

data class CreateLoadingTipRequest(
    val content: String,
    val displayOrder: Int = 0
)

data class UpdateLoadingTipRequest(
    val content: String? = null,
    val displayOrder: Int? = null,
    val isActive: Boolean? = null
)
