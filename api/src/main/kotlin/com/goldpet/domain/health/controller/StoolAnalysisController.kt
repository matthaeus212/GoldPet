package com.goldpet.domain.health.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.health.dto.RequestStoolAnalysisRequest
import com.goldpet.domain.health.dto.HealthTrendResponse
import com.goldpet.domain.health.dto.StoolAnalysisResponse
import com.goldpet.domain.health.service.StoolAnalysisService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "Health", description = "반려동물 건강 분석 API")
@RestController
@RequestMapping("/api/v1/health/stool-analyses")
class StoolAnalysisController(
    private val stoolAnalysisService: StoolAnalysisService
) {

    @Operation(summary = "대변 분석 요청")
    @PostMapping
    fun requestAnalysis(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: RequestStoolAnalysisRequest
    ): ResponseEntity<StoolAnalysisResponse> {
        val response = stoolAnalysisService.requestAnalysis(principal.id, request)
        return ResponseEntity.created(URI.create("/api/v1/health/stool-analyses/${response.id}")).body(response)
    }

    @Operation(summary = "분석 결과 조회 (폴링)")
    @GetMapping("/{id}")
    fun getAnalysis(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<StoolAnalysisResponse> {
        return ResponseEntity.ok(stoolAnalysisService.getAnalysis(id, principal.id))
    }

    @Operation(summary = "펫별 분석 이력")
    @GetMapping("/pet/{petId}")
    fun getAnalysisHistory(
        @PathVariable petId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<StoolAnalysisResponse>> {
        return ResponseEntity.ok(stoolAnalysisService.getAnalysisHistory(petId, principal.id, pageable))
    }

    @Operation(summary = "펫 건강 트렌드")
    @GetMapping("/pet/{petId}/trend")
    fun getHealthTrend(
        @PathVariable petId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "6") months: Int
    ): ResponseEntity<HealthTrendResponse> {
        return ResponseEntity.ok(stoolAnalysisService.getHealthTrend(petId, principal.id, months))
    }

    @Operation(summary = "내 분석 전체 목록")
    @GetMapping("/my")
    fun getMyAnalyses(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<StoolAnalysisResponse>> {
        return ResponseEntity.ok(stoolAnalysisService.getMyAnalyses(principal.id, pageable))
    }
}
