package com.goldpet.domain.metrics.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.metrics.service.DailyActiveService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * App-open DAU signal. The client calls this once per session on app resume.
 * Authentication is enforced by SecurityConfig's `.anyRequest().authenticated()`
 * (no permitAll entry needed). The handler returns immediately; the daily-active
 * row is written asynchronously so this endpoint never blocks the app.
 */
@Tag(name = "Session", description = "세션/활성 사용자(DAU) 신호 API")
@RestController
@RequestMapping("/api/v1/session")
class SessionController(
    private val dailyActiveService: DailyActiveService,
) {
    @Operation(summary = "앱 오픈 활성 신호 기록 (DAU)")
    @PostMapping("/active")
    fun recordActive(
        @AuthenticationPrincipal principal: UserPrincipal?,
    ): ResponseEntity<Void> {
        if (principal == null) return ResponseEntity.status(401).build()
        dailyActiveService.recordActiveAsync(principal.id)
        return ResponseEntity.ok().build()
    }
}
