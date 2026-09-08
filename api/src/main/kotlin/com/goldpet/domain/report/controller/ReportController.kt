package com.goldpet.domain.report.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.report.dto.CreateReportRequest
import com.goldpet.domain.report.dto.ReportResponse
import com.goldpet.domain.report.service.ReportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "Report", description = "신고 API")
@RestController
@RequestMapping("/api/v1/reports")
class ReportController(
    private val reportService: ReportService
) {
    @Operation(summary = "신고하기", description = "게시글, 댓글, 사용자, 채팅을 신고합니다.")
    @PostMapping
    fun createReport(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateReportRequest
    ): ResponseEntity<ReportResponse> {
        val response = reportService.createReport(principal.id, request)
        return ResponseEntity.created(URI.create("/v1/reports/${response.id}")).body(response)
    }

    @Operation(summary = "내 신고 목록", description = "내가 제출한 신고 목록을 조회합니다.")
    @GetMapping("/my")
    fun getMyReports(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<List<ReportResponse>> {
        return ResponseEntity.ok(reportService.getMyReports(principal.id))
    }
}
