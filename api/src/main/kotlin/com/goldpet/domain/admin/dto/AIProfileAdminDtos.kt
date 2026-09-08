package com.goldpet.domain.admin.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldpet.domain.aiprofile.entity.AIProfileRequest
import com.goldpet.domain.aiprofile.entity.AIRequestStatus
import com.goldpet.domain.aiprofile.entity.AIRequestType
import com.goldpet.domain.aiprofile.entity.AIStyle
import java.time.LocalDate
import java.time.LocalDateTime

data class AIRequestAdminResponse(
    val id: Long,
    val userId: Long,
    val userNickname: String?,
    val petId: Long?,
    val petName: String?,
    val type: AIRequestType,
    val sourceImageUrl: String,
    val stylePrompt: String?,
    val status: AIRequestStatus,
    val resultUrl: String?,
    val errorMessage: String?,
    val goldCost: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(request: AIProfileRequest): AIRequestAdminResponse = AIRequestAdminResponse(
            id = request.id,
            userId = request.user.id,
            userNickname = request.user.nickname,
            petId = request.pet?.id,
            petName = request.pet?.name,
            type = request.type,
            sourceImageUrl = request.sourceImageUrl,
            stylePrompt = request.stylePrompt,
            status = request.status,
            resultUrl = request.resultUrl,
            errorMessage = request.errorMessage,
            goldCost = request.goldCost,
            createdAt = request.createdAt,
            updatedAt = request.updatedAt
        )
    }
}

data class AIStyleAdminRequest(
    val id: String,
    val name: String,
    val description: String,
    val previewUrl: String,
    val goldCost: Int,
    val isActive: Boolean = true,
    val displayOrder: Int = 0
)

data class AIStyleAdminResponse(
    val id: String,
    val name: String,
    val description: String?,
    val previewUrl: String?,
    val goldCost: Int,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val displayOrder: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(style: AIStyle): AIStyleAdminResponse = AIStyleAdminResponse(
            id = style.id,
            name = style.name,
            description = style.description,
            previewUrl = style.previewUrl,
            goldCost = style.goldCost,
            isActive = style.isActive,
            displayOrder = style.displayOrder,
            createdAt = style.createdAt,
            updatedAt = style.updatedAt
        )
    }
}

data class AIProfileStatsResponse(
    val totalRequests: Long,
    val pendingRequests: Long,
    val completedRequests: Long,
    val failedRequests: Long,
    val refundedRequests: Long,
    val totalGoldSpent: Long,
    val successRate: Double,
    val dailyBreakdown: List<DailyStatEntry>,
    val popularStyles: List<StyleUsageEntry>
)

data class StyleUsageEntry(
    val styleId: String,
    val styleName: String,
    val count: Int
)
