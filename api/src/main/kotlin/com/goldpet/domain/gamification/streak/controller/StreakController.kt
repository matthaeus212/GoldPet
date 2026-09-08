package com.goldpet.domain.gamification.streak.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.gamification.streak.dto.StreakResponse
import com.goldpet.domain.gamification.streak.service.StreakService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Streak", description = "연속 산책 스트릭 API (W2a)")
@RestController
@RequestMapping("/api/v1/streaks")
class StreakController(
    private val streakService: StreakService
) {
    @Operation(summary = "내 산책 스트릭 조회", description = "현재/최장 연속 일수, 프리즈 보유, 오늘 활동 여부")
    @GetMapping("/me")
    fun getMyStreak(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<StreakResponse> {
        return ResponseEntity.ok(streakService.getMyStreak(principal.id))
    }
}
