package com.goldpet.domain.common.controller

import com.goldpet.domain.common.dto.AppVersionResponse
import com.goldpet.domain.common.service.AppVersionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "App Version", description = "앱 버전 체크 및 강제 업데이트 API")
@RestController
@RequestMapping("/api/v1/app")
class AppVersionController(
    private val appVersionService: AppVersionService
) {

    @Operation(
        summary = "앱 버전 체크",
        description = "현재 버전을 기준으로 강제 업데이트 또는 선택 업데이트 여부를 반환합니다."
    )
    @GetMapping("/version")
    fun checkVersion(
        @RequestParam platform: String,
        @RequestParam currentVersion: String
    ): AppVersionResponse {
        return appVersionService.checkVersion(platform, currentVersion)
    }
}
