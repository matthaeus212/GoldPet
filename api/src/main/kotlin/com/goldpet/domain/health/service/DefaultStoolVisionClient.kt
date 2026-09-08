package com.goldpet.domain.health.service

import com.goldpet.domain.health.dto.PetContextDto
import com.goldpet.domain.health.dto.StoolAnalysisResult
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("test", "codegen")
class DefaultStoolVisionClient : StoolVisionClient {

    override fun analyze(imageUrl: String, petInfo: PetContextDto): StoolAnalysisResult {
        return StoolAnalysisResult(
            colorScore = 4,
            consistencyScore = 4,
            coatingScore = 5,
            contentsScore = 5,
            overallScore = 4,
            colorAssessment = "황갈색으로 정상 범위입니다.",
            consistencyAssessment = "적절한 굳기로 정상입니다.",
            coatingAssessment = "점액이 없어 정상입니다.",
            contentsAssessment = "이물질이 없습니다.",
            healthSummary = "전반적으로 건강한 대변 상태입니다. (테스트 결과)",
            healthTips = listOf("규칙적인 식사를 유지하세요.", "충분한 수분 섭취를 확인하세요."),
            warnings = emptyList()
        )
    }
}
