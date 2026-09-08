package com.goldpet.domain.common.controller

import com.goldpet.domain.common.dto.AppNoticeResponse
import com.goldpet.domain.common.service.AppNoticeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "App Notices", description = "앱 공지/팝업 API (인증 불필요)")
@RestController
@RequestMapping("/api/v1/app/notices")
class AppNoticeController(
    private val appNoticeService: AppNoticeService
) {

    @Operation(
        summary = "활성 공지/팝업 목록 조회",
        description = "현재 활성화된 공지/팝업 목록을 반환합니다. screen 파라미터로 특정 화면의 공지만 필터링할 수 있습니다."
    )
    @GetMapping
    fun getActiveNotices(
        @RequestParam(required = false) screen: String?,
        @RequestParam(required = false) type: String?
    ): ResponseEntity<List<AppNoticeResponse>> {
        return ResponseEntity.ok(appNoticeService.getActiveNotices(screen, type))
    }

    @Operation(
        summary = "점검 공지 조회",
        description = "현재 활성화된 서버 점검 공지를 반환합니다. 없으면 null을 반환합니다."
    )
    @GetMapping("/maintenance")
    fun getMaintenanceNotice(): ResponseEntity<AppNoticeResponse?> {
        return ResponseEntity.ok(appNoticeService.getMaintenanceNotice())
    }
}
