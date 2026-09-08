package com.goldpet.domain.common.controller

import com.goldpet.domain.common.service.SystemSettingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "System Settings", description = "Public System Configuration API")
@RestController
@RequestMapping("/api/v1/settings")
class SystemSettingController(
    private val systemSettingService: SystemSettingService
) {

    @Operation(summary = "Get Public Settings", description = "Returns valid public system configurations")
    @GetMapping("/public")
    fun getPublicSettings(): Map<String, Any> {
        return mapOf(
            "communityPostPreviewLength" to systemSettingService.getInt("COMMUNITY_POST_PREVIEW_LENGTH", 100),
            "walkMinDurationSeconds" to systemSettingService.getInt("WALK_MIN_DURATION_SECONDS", 30),
            "walkMinDistanceMeters" to systemSettingService.getInt("WALK_MIN_DISTANCE_METERS", 10),
            // community-author-profile-gallery §4-2 — flag off 일 때 프론트는 프로필 탭 onClick 자체를
            // 바인딩하지 않는다. seed 는 V58 migration 에서 동일 키로 `false` insert 됨.
            "authorProfileLinkEnabled" to systemSettingService.getBoolean("community.author_profile_link.enabled", false)
        )
    }
}
