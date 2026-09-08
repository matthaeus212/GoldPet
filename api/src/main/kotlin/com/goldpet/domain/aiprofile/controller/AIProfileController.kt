package com.goldpet.domain.aiprofile.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.admin.service.LoadingTipAdminService
import com.goldpet.domain.aiprofile.dto.AILoadingTipResponse
import com.goldpet.domain.aiprofile.dto.AIProfileRequestResponse
import com.goldpet.domain.aiprofile.dto.AIStyleOption
import com.goldpet.domain.aiprofile.dto.ApplyProfileRequest
import com.goldpet.domain.aiprofile.dto.CreateAIProfileRequest
import com.goldpet.domain.aiprofile.service.AIProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "AI Profile", description = "AI 프로필 API")
@RestController
@RequestMapping("/api/v1/ai-profile")
class AIProfileController(
    private val aiProfileService: AIProfileService,
    private val loadingTipAdminService: LoadingTipAdminService
) {
    @Operation(summary = "AI 생성 대기 로딩 팁 목록")
    @GetMapping("/loading-tips")
    fun getLoadingTips(): ResponseEntity<List<AILoadingTipResponse>> {
        return ResponseEntity.ok(loadingTipAdminService.getActiveTips())
    }

    @Operation(summary = "스타일 옵션 목록")
    @GetMapping("/styles")
    fun getStyles(): ResponseEntity<List<AIStyleOption>> {
        return ResponseEntity.ok(aiProfileService.getStyles())
    }

    @Operation(summary = "AI 프로필 요청 생성")
    @PostMapping("/requests")
    fun createRequest(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateAIProfileRequest
    ): ResponseEntity<AIProfileRequestResponse> {
        val response = aiProfileService.createRequest(principal.id, request)
        return ResponseEntity.created(URI.create("/v1/ai-profile/requests/${response.id}")).body(response)
    }

    @Operation(summary = "내 요청 목록")
    @GetMapping("/requests")
    fun getMyRequests(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<AIProfileRequestResponse>> {
        return ResponseEntity.ok(aiProfileService.getMyRequests(principal.id, pageable))
    }

    @Operation(summary = "요청 상세")
    @GetMapping("/requests/{requestId}")
    fun getRequest(@PathVariable requestId: Long): ResponseEntity<AIProfileRequestResponse> {
        return ResponseEntity.ok(aiProfileService.getRequest(requestId))
    }

    @Operation(summary = "펫별 요청 목록")
    @GetMapping("/pets/{petId}/requests")
    fun getPetRequests(
        @PathVariable petId: Long,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<AIProfileRequestResponse>> {
        return ResponseEntity.ok(aiProfileService.getPetRequests(petId, pageable))
    }

    @Operation(summary = "대기중인 요청 수")
    @GetMapping("/pending-count")
    fun getPendingCount(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<Map<String, Long>> {
        val count = aiProfileService.getPendingCount(principal.id)
        return ResponseEntity.ok(mapOf("count" to count))
    }

    @Operation(summary = "요청 취소")
    @DeleteMapping("/requests/{requestId}")
    fun cancelRequest(
        @PathVariable requestId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        aiProfileService.cancelRequest(requestId, principal.id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "AI 생성 결과를 펫 프로필에 적용")
    @PostMapping("/requests/{requestId}/apply")
    fun applyResultAsProfile(
        @PathVariable requestId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody applyRequest: ApplyProfileRequest
    ): ResponseEntity<AIProfileRequestResponse> {
        val response = aiProfileService.applyResultAsProfile(
            requestId,
            principal.id,
            applyRequest.petId,
            applyRequest.applyAs,
            applyRequest.forceReplace ?: false
        )
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "일일 생성 사용량 조회")
    @GetMapping("/daily-usage")
    fun getDailyUsage(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Map<String, Int>> {
        return ResponseEntity.ok(aiProfileService.getDailyUsage(principal.id))
    }
}
