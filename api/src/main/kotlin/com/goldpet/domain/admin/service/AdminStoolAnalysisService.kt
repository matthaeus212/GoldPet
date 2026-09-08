package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.AdminStoolAnalysisItem
import com.goldpet.domain.admin.controller.DailyCount
import com.goldpet.domain.admin.controller.StoolAnalysisStatsResponse
import com.goldpet.domain.walk.entity.AnalysisStatus
import com.goldpet.domain.walk.repository.StoolAnalysisRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class AdminStoolAnalysisService(
    private val stoolAnalysisRepository: StoolAnalysisRepository
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val costPerAnalysis = 50L

    @Transactional(readOnly = true)
    fun getStats(): StoolAnalysisStatsResponse {
        val total = stoolAnalysisRepository.count()
        val since = LocalDateTime.now().minusDays(30)

        val dailyCounts = stoolAnalysisRepository.dailyCountsSince(since).map { row ->
            DailyCount(date = LocalDate.parse(row.getDate()), count = row.getCount())
        }

        val statusDistribution = mapOf(
            AnalysisStatus.PENDING.name to stoolAnalysisRepository.countByStatus(AnalysisStatus.PENDING),
            AnalysisStatus.ANALYZING.name to stoolAnalysisRepository.countByStatus(AnalysisStatus.ANALYZING),
            AnalysisStatus.COMPLETED.name to stoolAnalysisRepository.countByStatus(AnalysisStatus.COMPLETED),
            AnalysisStatus.FAILED.name to stoolAnalysisRepository.countByStatus(AnalysisStatus.FAILED)
        )

        return StoolAnalysisStatsResponse(
            totalCount = total,
            dailyCounts = dailyCounts,
            avgOverallScore = stoolAnalysisRepository.avgOverallScore(),
            statusDistribution = statusDistribution,
            estimatedCostKrw = total * costPerAnalysis
        )
    }

    @Transactional(readOnly = true)
    fun getAnalyses(status: String?, pageable: Pageable): Page<AdminStoolAnalysisItem> {
        val statusEnum = status?.let {
            try { AnalysisStatus.valueOf(it) } catch (e: Exception) { null }
        }
        return stoolAnalysisRepository.findAllForAdmin(statusEnum, pageable).map { a ->
            AdminStoolAnalysisItem(
                id = a.id,
                petId = a.pet.id,
                petName = a.pet.name,
                userId = a.user.id,
                userNickname = a.user.nickname ?: "사용자",
                overallScore = a.overallScore,
                status = a.status.name,
                createdAt = a.createdAt?.format(formatter)
            )
        }
    }
}
