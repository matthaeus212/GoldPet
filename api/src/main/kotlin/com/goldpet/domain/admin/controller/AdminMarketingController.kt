package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.dto.*
import com.goldpet.domain.admin.service.AdminMarketingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin Marketing", description = "관리자 마케팅 관리 API")
@RestController
@RequestMapping("/api/v1/admin/marketing")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminMarketingController(
    private val adminMarketingService: AdminMarketingService
) {
    @Operation(summary = "배너 목록 조회")
    @GetMapping("/banners")
    fun getBanners(
        @RequestParam(required = false) placement: String?
    ): ResponseEntity<List<BannerResponse>> {
        return ResponseEntity.ok(adminMarketingService.getBanners(placement))
    }

    @Operation(summary = "배너 생성")
    @PostMapping("/banners")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createBanner(@RequestBody request: BannerCreateRequest): ResponseEntity<BannerResponse> {
        return ResponseEntity.ok(adminMarketingService.createBanner(request))
    }

    @Operation(summary = "배너 수정")
    @PutMapping("/banners/{bannerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun updateBanner(
        @PathVariable bannerId: Long,
        @RequestBody request: BannerUpdateRequest
    ): ResponseEntity<BannerResponse> {
        return ResponseEntity.ok(adminMarketingService.updateBanner(bannerId, request))
    }

    @Operation(summary = "배너 삭제")
    @DeleteMapping("/banners/{bannerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteBanner(@PathVariable bannerId: Long): ResponseEntity<Void> {
        adminMarketingService.deleteBanner(bannerId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "캠페인 목록 조회")
    @GetMapping("/campaigns")
    fun getCampaigns(): ResponseEntity<List<CampaignResponse>> {
        return ResponseEntity.ok(adminMarketingService.getCampaigns())
    }

    @Operation(summary = "캠페인 생성")
    @PostMapping("/campaigns")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createCampaign(@RequestBody request: CampaignCreateRequest): ResponseEntity<CampaignResponse> {
        return ResponseEntity.ok(adminMarketingService.createCampaign(request))
    }

    @Operation(summary = "알림 템플릿 목록 조회")
    @GetMapping("/notifications/templates")
    fun getTemplates(): ResponseEntity<List<NotificationTemplateResponse>> {
        return ResponseEntity.ok(adminMarketingService.getTemplates())
    }

    @Operation(summary = "알림 템플릿 생성")
    @PostMapping("/notifications/templates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createTemplate(@RequestBody request: NotificationTemplateRequest): ResponseEntity<NotificationTemplateResponse> {
        return ResponseEntity.ok(adminMarketingService.createTemplate(request))
    }

    @Operation(summary = "알림 템플릿 수정")
    @PutMapping("/notifications/templates/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun updateTemplate(
        @PathVariable id: Long,
        @RequestBody request: NotificationTemplateRequest
    ): ResponseEntity<NotificationTemplateResponse> {
        return ResponseEntity.ok(adminMarketingService.updateTemplate(id, request))
    }

    @Operation(summary = "알림 템플릿 삭제")
    @DeleteMapping("/notifications/templates/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteTemplate(@PathVariable id: Long): ResponseEntity<Void> {
        adminMarketingService.deleteTemplate(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "알림 발송")
    @PostMapping("/notifications/send")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun sendNotification(@RequestBody request: SendNotificationRequest): ResponseEntity<SendNotificationResult> {
        return ResponseEntity.ok(adminMarketingService.sendNotification(request))
    }

    @Operation(summary = "발송 로그 조회")
    @GetMapping("/notifications/logs")
    fun getDeliveryLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<List<DeliveryLogResponse>> {
        return ResponseEntity.ok(adminMarketingService.getDeliveryLogs(page, size))
    }
}
