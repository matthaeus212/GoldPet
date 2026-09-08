package com.goldpet.domain.gamification.controller

import com.goldpet.domain.gamification.dto.BadgeResponse
import com.goldpet.domain.gamification.dto.DailyMissionResponse
import com.goldpet.domain.gamification.service.DailyMissionService
import com.goldpet.domain.gamification.service.GamificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "게임화", description = "뱃지 등 게임화 기능")
@RestController
@RequestMapping("/api/v1/gamification")
class GamificationController(
    private val gamificationService: GamificationService,
    private val dailyMissionService: DailyMissionService
) {

    @Operation(summary = "오늘의 데일리 미션 조회", description = "오늘(KST) 노출 미션 3종 + 완료/진행 상태")
    @GetMapping("/missions/today")
    fun getTodayMissions(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<DailyMissionResponse> {
        return ResponseEntity.ok(dailyMissionService.getTodayMissions(userDetails.id))
    }

    @Operation(summary = "전체 뱃지 목록 조회")
    @GetMapping("/badges")
    fun getAllBadges(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<List<BadgeResponse>> {
        val badges = gamificationService.getAllBadgesWithStatus(userDetails.id)
        return ResponseEntity.ok(badges)
    }

    @Operation(summary = "내 획득 뱃지 조회")
    @GetMapping("/badges/my")
    fun getMyBadges(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<List<BadgeResponse>> {
        val badges = gamificationService.getMyBadges(userDetails.id)
        return ResponseEntity.ok(badges)
    }
}
