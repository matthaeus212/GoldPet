package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.service.LoadingTipAdminService
import com.goldpet.domain.aiprofile.dto.AILoadingTipResponse
import com.goldpet.domain.aiprofile.dto.CreateLoadingTipRequest
import com.goldpet.domain.aiprofile.dto.UpdateLoadingTipRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "Admin - Loading Tips", description = "AI 로딩 팁 관리 API")
@RestController
@RequestMapping("/api/v1/admin/ai-profile/loading-tips")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminLoadingTipController(
    private val loadingTipAdminService: LoadingTipAdminService
) {

    @Operation(summary = "전체 로딩 팁 조회")
    @GetMapping
    fun getAllTips(): ResponseEntity<List<AILoadingTipResponse>> {
        return ResponseEntity.ok(loadingTipAdminService.getAllTips())
    }

    @Operation(summary = "로딩 팁 생성")
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createTip(@RequestBody request: CreateLoadingTipRequest): ResponseEntity<AILoadingTipResponse> {
        val response = loadingTipAdminService.createTip(request)
        return ResponseEntity.created(URI.create("/api/v1/admin/ai-profile/loading-tips/${response.id}")).body(response)
    }

    @Operation(summary = "로딩 팁 수정")
    @PutMapping("/{tipId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun updateTip(
        @PathVariable tipId: Int,
        @RequestBody request: UpdateLoadingTipRequest
    ): ResponseEntity<AILoadingTipResponse> {
        return ResponseEntity.ok(loadingTipAdminService.updateTip(tipId, request))
    }

    @Operation(summary = "로딩 팁 삭제")
    @DeleteMapping("/{tipId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteTip(@PathVariable tipId: Int): ResponseEntity<Void> {
        loadingTipAdminService.deleteTip(tipId)
        return ResponseEntity.noContent().build()
    }
}
