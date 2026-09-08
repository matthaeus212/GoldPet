package com.goldpet.domain.admin.controller

import com.goldpet.domain.common.dto.AppNoticeResponse
import com.goldpet.domain.common.dto.CreateAppNoticeRequest
import com.goldpet.domain.common.dto.UpdateAppNoticeRequest
import com.goldpet.domain.common.service.AppNoticeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin Notices", description = "관리자 공지/팝업 관리 API")
@RestController
@RequestMapping("/api/v1/admin/notices")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminNoticeController(
    private val appNoticeService: AppNoticeService
) {

    @Operation(summary = "공지/팝업 목록 조회", description = "비활성 항목을 포함한 전체 목록을 반환합니다.")
    @GetMapping
    fun getAllNotices(): ResponseEntity<List<AppNoticeResponse>> {
        return ResponseEntity.ok(appNoticeService.getAllNotices())
    }

    @Operation(summary = "공지/팝업 생성")
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createNotice(
        @RequestBody request: CreateAppNoticeRequest
    ): ResponseEntity<AppNoticeResponse> {
        return ResponseEntity.ok(appNoticeService.createNotice(request))
    }

    @Operation(summary = "공지/팝업 수정")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun updateNotice(
        @PathVariable id: Long,
        @RequestBody request: UpdateAppNoticeRequest
    ): ResponseEntity<AppNoticeResponse> {
        return ResponseEntity.ok(appNoticeService.updateNotice(id, request))
    }

    @Operation(summary = "공지/팝업 삭제 (소프트 삭제 - isActive=false)", description = "실제 삭제 대신 비활성화합니다.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteNotice(@PathVariable id: Long): ResponseEntity<Void> {
        appNoticeService.deleteNotice(id)
        return ResponseEntity.noContent().build()
    }
}
