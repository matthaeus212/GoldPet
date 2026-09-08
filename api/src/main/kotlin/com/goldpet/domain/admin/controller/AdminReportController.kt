package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.service.AdminReportService
import com.goldpet.domain.report.entity.ReportActionType
import com.goldpet.domain.report.entity.ReportStatus
import com.goldpet.domain.report.entity.ReportType
import com.goldpet.config.security.AdminUserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin Report Management", description = "관리자 신고 관리 API")
@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminReportController(
    private val adminReportService: AdminReportService
) {
    @Operation(summary = "신고 목록 조회")
    @GetMapping
    fun getReports(
        @RequestParam(required = false) status: String?,
        pageable: Pageable
    ): ResponseEntity<Page<ReportAdminResponse>> {
        return ResponseEntity.ok(adminReportService.getReports(status, pageable))
    }

    @Operation(summary = "신고 단건 조회")
    @GetMapping("/{reportId}")
    fun getReport(@PathVariable reportId: Long): ResponseEntity<ReportAdminResponse> {
        return ResponseEntity.ok(adminReportService.getReport(reportId))
    }

    @Operation(summary = "신고 처리 (조치)")
    @PostMapping("/{reportId}/resolve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun resolveReport(
        @PathVariable reportId: Long,
        @RequestBody request: ResolveReportRequest,
        @AuthenticationPrincipal principal: AdminUserPrincipal
    ): ResponseEntity<Void> {
        adminReportService.resolveReport(reportId, principal.id, request)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "신고 기각")
    @PostMapping("/{reportId}/dismiss")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun dismissReport(
        @PathVariable reportId: Long,
        @AuthenticationPrincipal principal: AdminUserPrincipal
    ): ResponseEntity<Void> {
        adminReportService.dismissReport(reportId, principal.id)
        return ResponseEntity.ok().build()
    }
}

data class ResolveReportRequest(
    val actionType: ReportActionType,
    val adminNote: String? = null
)

data class ReportAdminResponse(
    val id: Long,
    val type: ReportType,
    val targetId: Long,
    val targetPreview: String?,
    val reason: String,
    val reporterNickname: String,
    val reporterId: Long,
    val status: ReportStatus,
    val createdAt: String?,
    val resolvedAt: String?,
    val actionType: ReportActionType? = null,
    val adminNote: String? = null,
    val resolvedByName: String? = null,
    val targetImageUrl: String? = null,
    val targetImageKey: String? = null
)
