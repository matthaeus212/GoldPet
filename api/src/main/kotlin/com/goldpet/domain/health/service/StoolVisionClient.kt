package com.goldpet.domain.health.service

import com.goldpet.domain.health.dto.PetContextDto
import com.goldpet.domain.health.dto.StoolAnalysisResult

interface StoolVisionClient {
    fun analyze(imageUrl: String, petInfo: PetContextDto): StoolAnalysisResult
}
