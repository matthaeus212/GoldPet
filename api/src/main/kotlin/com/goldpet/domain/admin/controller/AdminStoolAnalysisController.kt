package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.service.AdminStoolAnalysisService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Tag(name = "Admin Stool Analysis", description = "관리자 배변 분석 관리 API")
@RestController
@RequestMapping("/api/v1/admin/stool-analyses")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminStoolAnalysisController(
    private val adminStoolAnalysisService: AdminStoolAnalysisService
) {
    @Operation(summary = "배변 분석 통계 조회")
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<StoolAnalysisStatsResponse> {
        return ResponseEntity.ok(adminStoolAnalysisService.getStats())
    }

    @Operation(summary = "배변 분석 목록 조회")
    @GetMapping
    fun getAnalyses(
        @RequestParam(required = false) status: String?,
        pageable: Pageable
    ): ResponseEntity<Page<AdminStoolAnalysisItem>> {
        return ResponseEntity.ok(adminStoolAnalysisService.getAnalyses(status, pageable))
    }
}

data class StoolAnalysisStatsResponse(
    val totalCount: Long,
    val dailyCounts: List<DailyCount>,
    val avgOverallScore: Double?,
    val statusDistribution: Map<String, Long>,
    val estimatedCostKrw: Long
)

data class DailyCount(
    val date: LocalDate,
    val count: Long
)

data class AdminStoolAnalysisItem(
    val id: Long,
    val petId: Long,
    val petName: String,
    val userId: Long,
    val userNickname: String,
    val overallScore: Int?,
    val status: String,
    val createdAt: String?
)
