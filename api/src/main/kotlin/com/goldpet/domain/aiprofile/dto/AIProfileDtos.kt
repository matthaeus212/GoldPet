package com.goldpet.domain.aiprofile.dto

import com.goldpet.domain.aiprofile.entity.AIProfileRequest
import com.goldpet.domain.aiprofile.entity.AIRequestStatus
import com.goldpet.domain.aiprofile.entity.AIRequestType
import java.time.LocalDateTime

data class CreateAIProfileRequest(
    val petId: Long? = null,
    val type: AIRequestType,
    val sourceImageUrl: String,
    val stylePrompt: String? = null,
    val petType: String? = null,
    val mode: String? = null
)

data class AIProfileRequestResponse(
    val id: Long,
    val petId: Long?,
    val petName: String?,
    val type: AIRequestType,
    val sourceImageUrl: String,
    val stylePrompt: String?,
    val status: AIRequestStatus,
    val resultUrl: String?,
    val errorMessage: String?,
    val goldCost: Int,
    val createdAt: LocalDateTime?
) {
    companion object {
        fun from(request: AIProfileRequest) = AIProfileRequestResponse(
            id = request.id,
            petId = request.pet?.id,
            petName = request.pet?.name,
            type = request.type,
            sourceImageUrl = request.sourceImageUrl,
            stylePrompt = request.stylePrompt,
            status = request.status,
            resultUrl = request.resultUrl,
            errorMessage = request.errorMessage,
            goldCost = request.goldCost,
            createdAt = request.createdAt
        )
    }
}

data class AIStyleOption(
    val id: String,
    val name: String,
    val description: String,
    val previewUrl: String,
    val goldCost: Int,
    val presetId: String? = null
)

data class ApplyProfileRequest(
    val applyAs: String,  // "MAIN" or "ADDITIONAL"
    val petId: Long,
    /** MAIN 분기에서 최대 장수 도달 시 기존 대표 이미지를 교체할지 여부. */
    val forceReplace: Boolean? = null
)
