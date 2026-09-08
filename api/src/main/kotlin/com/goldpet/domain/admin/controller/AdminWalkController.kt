package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.dto.BestWalkCoupleRequest
import com.goldpet.domain.admin.dto.BestWalkCoupleResponse
import com.goldpet.domain.admin.dto.WalkAdminResponse
import com.goldpet.domain.admin.dto.WalkDeleteRequest
import com.goldpet.domain.admin.dto.WalkDetailResponse
import com.goldpet.domain.admin.dto.WalkRankingAdminResponse
import com.goldpet.domain.admin.dto.WalkStatsDetailResponse
import com.goldpet.domain.admin.service.AdminWalkService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Tag(name = "Admin Walk Management", description = "관리자 산책 관리 API")
@RestController
@RequestMapping("/api/v1/admin/walks")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminWalkController(
    private val adminWalkService: AdminWalkService
) {
    @Operation(summary = "산책 목록 조회")
    @GetMapping
    fun getWalks(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) userNickname: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(required = false) minDistance: Double?,
        @RequestParam(required = false) maxDistance: Double?
    ): ResponseEntity<Page<WalkAdminResponse>> {
        return ResponseEntity.ok(adminWalkService.getWalks(page, size, userNickname, startDate, endDate, minDistance, maxDistance))
    }

    @Operation(summary = "산책 상세 조회")
    @GetMapping("/{walkId}")
    fun getWalkDetail(@PathVariable walkId: Long): ResponseEntity<WalkDetailResponse> {
        return ResponseEntity.ok(adminWalkService.getWalkDetail(walkId))
    }

    @Operation(summary = "산책 삭제 (관리)")
    @DeleteMapping("/{walkId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteWalk(
        @PathVariable walkId: Long,
        @RequestBody request: WalkDeleteRequest
    ): ResponseEntity<Void> {
        adminWalkService.deleteWalk(walkId, request.reason)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "산책 상세 통계")
    @GetMapping("/stats/detailed")
    fun getDetailedStats(
        @RequestParam(required = false) period: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): ResponseEntity<WalkStatsDetailResponse> {
        return ResponseEntity.ok(adminWalkService.getDetailedStats(period, startDate, endDate))
    }

    @Operation(summary = "월별 산책 랭킹 조회 (베스트 커플 선정용)")
    @GetMapping("/ranking")
    fun getAdminRanking(
        @RequestParam yearMonth: String,
        @RequestParam(defaultValue = "50") size: Int
    ): ResponseEntity<List<WalkRankingAdminResponse>> {
        return ResponseEntity.ok(adminWalkService.getAdminRanking(yearMonth, size))
    }

    @Operation(summary = "베스트 산책 커플 조회")
    @GetMapping("/best-couple")
    fun getBestCouple(
        @RequestParam yearMonth: String
    ): ResponseEntity<BestWalkCoupleResponse> {
        return ResponseEntity.ok(adminWalkService.getBestCouple(yearMonth))
    }

    @Operation(summary = "베스트 산책 커플 선정 (upsert)")
    @PostMapping("/best-couple")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun setBestCouple(
        @RequestBody request: BestWalkCoupleRequest
    ): ResponseEntity<BestWalkCoupleResponse> {
        return ResponseEntity.ok(adminWalkService.setBestCouple(request))
    }

    @Operation(summary = "베스트 산책 커플 해제")
    @DeleteMapping("/best-couple/{yearMonth}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteBestCouple(
        @PathVariable yearMonth: String
    ): ResponseEntity<Void> {
        adminWalkService.deleteBestCouple(yearMonth)
        return ResponseEntity.noContent().build()
    }
}
