package com.goldpet.domain.aiprofile.service

import com.goldpet.domain.aiprofile.entity.AIProfileRequest

interface AIGenerationClient {
    fun requestGeneration(aiRequest: AIProfileRequest)
}
