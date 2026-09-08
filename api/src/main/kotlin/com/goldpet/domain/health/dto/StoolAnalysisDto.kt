package com.goldpet.domain.health.dto

import com.goldpet.domain.pet.entity.Pet
import com.goldpet.domain.walk.entity.AnalysisStatus
import com.goldpet.domain.walk.entity.StoolAnalysis
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class RequestStoolAnalysisRequest(
    val walkSpotId: Long? = null,
    val petId: Long,
    val imageUrl: String
)

data class StoolAnalysisResponse(
    val id: Long,
    val petId: Long,
    val imageUrl: String,
    val status: AnalysisStatus,
    val colorScore: Int?,
    val consistencyScore: Int?,
    val coatingScore: Int?,
    val contentsScore: Int?,
    val overallScore: Int?,
    val colorAssessment: String?,
    val consistencyAssessment: String?,
    val coatingAssessment: String?,
    val contentsAssessment: String?,
    val healthSummary: String?,
    val healthTips: String?,
    val warnings: String?,
    val errorMessage: String?,
    val analyzedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val disclaimer: String = "AI 분석은 참고용이며, 정확한 진단은 수의사와 상담하세요."
) {
    companion object {
        fun from(entity: StoolAnalysis) = StoolAnalysisResponse(
            id = entity.id,
            petId = entity.pet.id,
            imageUrl = entity.imageUrl,
            status = entity.status,
            colorScore = entity.colorScore,
            consistencyScore = entity.consistencyScore,
            coatingScore = entity.coatingScore,
            contentsScore = entity.contentsScore,
            overallScore = entity.overallScore,
            colorAssessment = entity.colorAssessment,
            consistencyAssessment = entity.consistencyAssessment,
            coatingAssessment = entity.coatingAssessment,
            contentsAssessment = entity.contentsAssessment,
            healthSummary = entity.healthSummary,
            healthTips = entity.healthTips,
            warnings = entity.warnings,
            errorMessage = entity.errorMessage,
            analyzedAt = entity.analyzedAt,
            createdAt = entity.createdAt
        )
    }
}

data class HealthTrendResponse(
    val petId: Long,
    val petName: String,
    val monthlyScores: List<MonthlyScore>,
    val totalAnalyses: Long,
    /** 첫↔마지막 달 종합점수 비교 요약 (데이터 2개월 미만이면 null) */
    val summary: TrendSummary? = null
)

data class MonthlyScore(
    val yearMonth: String,
    val averageScore: Double,
    // 4C 세부 월 평균 (null = 해당 월 데이터 없음)
    val colorAvg: Double? = null,
    val consistencyAvg: Double? = null,
    val coatingAvg: Double? = null,
    val contentsAvg: Double? = null,
    val count: Int
)

data class TrendSummary(
    /** IMPROVING | DECLINING | STABLE */
    val direction: String,
    /** 마지막 달 − 첫 달 종합 평균 (양수=개선) */
    val deltaOverall: Double,
    val firstMonth: String?,
    val lastMonth: String?
) {
    companion object {
        /** ±0.5 데드존: 그 안은 STABLE. */
        fun directionOf(delta: Double): String = when {
            delta >= 0.5 -> "IMPROVING"
            delta <= -0.5 -> "DECLINING"
            else -> "STABLE"
        }
    }
}

data class PetContextDto(
    val species: String,
    val breed: String?,
    val ageMonths: Int?,
    val weightKg: Double?
)

data class StoolAnalysisResult(
    val colorScore: Int,
    val consistencyScore: Int,
    val coatingScore: Int,
    val contentsScore: Int,
    val overallScore: Int,
    val colorAssessment: String,
    val consistencyAssessment: String,
    val coatingAssessment: String,
    val contentsAssessment: String,
    val healthSummary: String,
    val healthTips: List<String>,
    val warnings: List<String>
)

fun Pet.toPetContextDto(): PetContextDto {
    val ageMonths = birthDate?.let {
        ChronoUnit.MONTHS.between(it, LocalDate.now()).toInt().takeIf { months -> months >= 0 }
    }
    return PetContextDto(
        species = species.name,
        breed = breed?.name,
        ageMonths = ageMonths,
        weightKg = weightKg
    )
}
