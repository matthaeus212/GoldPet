package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.dto.AIProfileStatsResponse
import com.goldpet.domain.admin.dto.AIRequestAdminResponse
import com.goldpet.domain.admin.dto.AIStyleAdminRequest
import com.goldpet.domain.admin.dto.AIStyleAdminResponse
import com.goldpet.domain.admin.service.AdminAIProfileService
import com.goldpet.domain.aiprofile.entity.AIRequestStatus
import com.goldpet.domain.aiprofile.service.AIProfileLegacyBackfillService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin AI Profile", description = "관리자 AI 프로필 관리 API")
@RestController
@RequestMapping("/api/v1/admin/ai-profile")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminAIProfileController(
    private val adminAIProfileService: AdminAIProfileService,
    private val aiProfileLegacyBackfillService: AIProfileLegacyBackfillService
) {
    @Operation(
        summary = "T1-7b 레거시 AI 프로필 URL → FileAttachment 역삽입",
        description = "T1-7a 배포 이전 `ai-profiles/{id}.jpg` 경로로 저장된 AI 결과에 대해 " +
            "FileAttachment 로우를 역삽입한다. 멱등. 실제 variant 생성은 이어서 " +
            "`POST /api/v1/admin/files/backfill?scope=FILE_ATTACHMENTS` 호출 필요."
    )
    @PostMapping("/backfill-legacy")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun backfillLegacy(
        @RequestParam(defaultValue = "true") dryRun: Boolean
    ): ResponseEntity<AIProfileLegacyBackfillService.Result> {
        return ResponseEntity.ok(aiProfileLegacyBackfillService.backfillLegacyAttachments(dryRun))
    }
    @Operation(summary = "AI 요청 목록 조회")
    @GetMapping("/requests")
    fun getRequests(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: AIRequestStatus?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) userId: Long?
    ): ResponseEntity<Page<AIRequestAdminResponse>> {
        return ResponseEntity.ok(adminAIProfileService.getRequests(page, size, status, type, userId))
    }

    @Operation(summary = "AI 요청 상세 조회")
    @GetMapping("/requests/{requestId}")
    fun getRequestDetail(@PathVariable requestId: Long): ResponseEntity<AIRequestAdminResponse> {
        return ResponseEntity.ok(adminAIProfileService.getRequestDetail(requestId))
    }

    @Operation(summary = "실패 요청 재시도")
    @PostMapping("/requests/{requestId}/retry")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun retryRequest(@PathVariable requestId: Long): ResponseEntity<AIRequestAdminResponse> {
        return ResponseEntity.ok(adminAIProfileService.retryRequest(requestId))
    }

    @Operation(summary = "실패 요청 환불")
    @PostMapping("/requests/{requestId}/refund")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun refundRequest(@PathVariable requestId: Long): ResponseEntity<Void> {
        adminAIProfileService.refundRequest(requestId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "대기/처리중 요청 강제 실패 처리")
    @PostMapping("/requests/{requestId}/force-fail")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun forceFailRequest(@PathVariable requestId: Long): ResponseEntity<Void> {
        adminAIProfileService.forceFailRequest(requestId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "스타일 목록 조회")
    @GetMapping("/styles")
    fun getStyles(): ResponseEntity<List<AIStyleAdminResponse>> {
        return ResponseEntity.ok(adminAIProfileService.getStyles())
    }

    @Operation(summary = "스타일 생성")
    @PostMapping("/styles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createStyle(@RequestBody request: AIStyleAdminRequest): ResponseEntity<AIStyleAdminResponse> {
        return ResponseEntity.ok(adminAIProfileService.createStyle(request))
    }

    @Operation(summary = "스타일 수정")
    @PutMapping("/styles/{styleId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun updateStyle(
        @PathVariable styleId: String,
        @RequestBody request: AIStyleAdminRequest
    ): ResponseEntity<AIStyleAdminResponse> {
        return ResponseEntity.ok(adminAIProfileService.updateStyle(styleId, request))
    }

    @Operation(summary = "스타일 삭제")
    @DeleteMapping("/styles/{styleId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteStyle(@PathVariable styleId: String): ResponseEntity<Void> {
        adminAIProfileService.deleteStyle(styleId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "스타일 활성/비활성 토글")
    @PatchMapping("/styles/{styleId}/toggle")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun toggleStyle(@PathVariable styleId: String): ResponseEntity<AIStyleAdminResponse> {
        return ResponseEntity.ok(adminAIProfileService.toggleStyle(styleId))
    }

    @Operation(summary = "AI 프로필 통계")
    @GetMapping("/stats")
    fun getStats(@RequestParam(required = false) period: String?): ResponseEntity<AIProfileStatsResponse> {
        return ResponseEntity.ok(adminAIProfileService.getStats())
    }
}
