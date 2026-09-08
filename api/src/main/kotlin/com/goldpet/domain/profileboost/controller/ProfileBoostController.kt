package com.goldpet.domain.profileboost.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.gold.service.IdempotencyService
import com.goldpet.domain.profileboost.dto.ActiveBoostResponse
import com.goldpet.domain.profileboost.dto.ProfileBoostResponse
import com.goldpet.domain.profileboost.service.ProfileBoostService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "프로필 부스트", description = "프로필 노출 부스트 (골드 sink)")
@RestController
@RequestMapping("/api/v1/profile-boost")
class ProfileBoostController(
    private val profileBoostService: ProfileBoostService,
    private val idempotencyService: IdempotencyService
) {

    @Operation(summary = "현재 활성 부스트 조회")
    @GetMapping("/active")
    fun getActiveBoost(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ActiveBoostResponse> {
        return ResponseEntity.ok(profileBoostService.getActiveBoost(principal.id))
    }

    @Operation(
        summary = "프로필 부스트 구매",
        description = "골드를 차감하고 부스트 윈도우 생성. **Idempotency-Key 헤더 필수** — 동일 key 재요청 시 저장된 응답 반환(중복 차감 방지)."
    )
    @PostMapping("/purchase")
    fun purchaseBoost(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): ResponseEntity<ProfileBoostResponse> {
        val hash = idempotencyService.hashRequest(principal.id, "POST /api/v1/profile-boost/purchase", REQUEST_MARKER)
        val response = idempotencyService.execute(
            key = idempotencyKey,
            userId = principal.id,
            requestHash = hash,
            responseType = ProfileBoostResponse::class.java
        ) {
            val boost = profileBoostService.purchaseBoost(principal.id)
            boost to boost.id
        }
        return ResponseEntity.ok(response)
    }

    companion object {
        // 바디 없는 구매 — idempotency hash 입력용 고정 마커.
        private const val REQUEST_MARKER = "profile-boost-purchase"
    }
}
