package com.goldpet.domain.health.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.exception.ForbiddenException
import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.health.dto.HealthTrendResponse
import com.goldpet.domain.health.dto.MonthlyScore
import com.goldpet.domain.health.dto.RequestStoolAnalysisRequest
import com.goldpet.domain.health.dto.StoolAnalysisResponse
import com.goldpet.domain.health.dto.TrendSummary
import com.goldpet.domain.health.event.StoolAnalysisRequestedEvent
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.walk.entity.AnalysisStatus
import com.goldpet.domain.walk.entity.StoolAnalysis
import com.goldpet.domain.walk.repository.StoolAnalysisRepository
import com.goldpet.domain.walk.repository.WalkSpotRepository
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
@Transactional(readOnly = true)
class StoolAnalysisService(
    private val stoolAnalysisRepository: StoolAnalysisRepository,
    private val petRepository: PetRepository,
    private val userRepository: UserRepository,
    private val walkSpotRepository: WalkSpotRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val systemSettingService: SystemSettingService,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun requestAnalysis(userId: Long, request: RequestStoolAnalysisRequest): StoolAnalysisResponse {
        val pet = petRepository.findById(request.petId).orElseThrow { NotFoundException("Pet not found") }

        if (pet.owner.id != userId) {
            throw ForbiddenException("You do not own this pet")
        }

        val dailyLimit = systemSettingService.getInt("stool.analysis.daily_limit", 10)
        val startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay()
        val todayCount = stoolAnalysisRepository.countByUserIdAndCreatedAtAfter(userId, startOfDay)
        if (todayCount >= dailyLimit) {
            throw BadRequestException("일일 분석 한도($dailyLimit 회)에 도달했습니다.")
        }

        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }

        val walkSpot = request.walkSpotId?.let {
            walkSpotRepository.findById(it).orElseThrow { NotFoundException("WalkSpot not found") }
        }

        val analysis = StoolAnalysis(
            walkSpot = walkSpot,
            pet = pet,
            user = user,
            imageUrl = request.imageUrl,
            status = AnalysisStatus.PENDING
        )
        val saved = stoolAnalysisRepository.save(analysis)

        applicationEventPublisher.publishEvent(StoolAnalysisRequestedEvent(saved.id))

        return StoolAnalysisResponse.from(saved)
    }

    fun getAnalysis(id: Long, userId: Long): StoolAnalysisResponse {
        val analysis = stoolAnalysisRepository.findById(id).orElseThrow { NotFoundException("Analysis not found") }
        if (analysis.user.id != userId) {
            throw ForbiddenException("Access denied")
        }
        return StoolAnalysisResponse.from(analysis)
    }

    fun getAnalysisHistory(petId: Long, userId: Long, pageable: Pageable): Page<StoolAnalysisResponse> {
        val pet = petRepository.findById(petId).orElseThrow { NotFoundException("Pet not found") }
        if (pet.owner.id != userId) {
            throw ForbiddenException("You do not own this pet")
        }
        return stoolAnalysisRepository.findByPetIdOrderByCreatedAtDesc(petId, pageable)
            .map { StoolAnalysisResponse.from(it) }
    }

    fun getHealthTrend(petId: Long, userId: Long, months: Int): HealthTrendResponse {
        val pet = petRepository.findById(petId).orElseThrow { NotFoundException("Pet not found") }
        if (pet.owner.id != userId) {
            throw ForbiddenException("You do not own this pet")
        }

        val since = LocalDateTime.now().minusMonths(months.toLong())
        val analyses = stoolAnalysisRepository.findByPetIdAndCreatedAtBetween(petId, since, LocalDateTime.now())
            .filter { it.status == AnalysisStatus.COMPLETED && it.overallScore != null }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
        fun avgOrNull(values: List<Int>): Double? = if (values.isEmpty()) null else values.average()
        val monthlyScores = analyses
            .groupBy { it.createdAt?.format(formatter) ?: "" }
            .filter { it.key.isNotEmpty() }
            .map { (yearMonth, group) ->
                MonthlyScore(
                    yearMonth = yearMonth,
                    averageScore = group.mapNotNull { it.overallScore }.average(),
                    colorAvg = avgOrNull(group.mapNotNull { it.colorScore }),
                    consistencyAvg = avgOrNull(group.mapNotNull { it.consistencyScore }),
                    coatingAvg = avgOrNull(group.mapNotNull { it.coatingScore }),
                    contentsAvg = avgOrNull(group.mapNotNull { it.contentsScore }),
                    count = group.size
                )
            }
            .sortedBy { it.yearMonth }

        // 첫↔마지막 달 종합점수 비교 요약 (2개월 이상일 때만; ±0.5 STABLE 데드존)
        val summary = if (monthlyScores.size >= 2) {
            val first = monthlyScores.first()
            val last = monthlyScores.last()
            val delta = last.averageScore - first.averageScore
            TrendSummary(TrendSummary.directionOf(delta), delta, first.yearMonth, last.yearMonth)
        } else null

        return HealthTrendResponse(
            petId = petId,
            petName = pet.name,
            monthlyScores = monthlyScores,
            totalAnalyses = stoolAnalysisRepository.countByPetId(petId),
            summary = summary
        )
    }

    fun getMyAnalyses(userId: Long, pageable: Pageable): Page<StoolAnalysisResponse> {
        return stoolAnalysisRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map { StoolAnalysisResponse.from(it) }
    }
}
