package com.goldpet.domain.user.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.common.exception.UnauthorizedException
import com.goldpet.domain.user.dto.PublicUserProfileResponse
import com.goldpet.domain.user.service.UserPublicProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * community-author-profile-gallery Phase 1 §4-1.
 *
 * 커뮤니티 작성자 프로필 탭 → `/users/:userId` 페이지가 헤더를 구성할 때 호출.
 * `SecurityConfig` (task #4) 에서 `authenticated()` 으로 잠긴다.
 */
@Tag(name = "User", description = "사용자 프로필 및 계정 API")
@RestController
@RequestMapping("/api/v1/users")
class UserPublicProfileController(
    private val userPublicProfileService: UserPublicProfileService,
) {
    @Operation(summary = "공개 프로필 조회 (타 유저)")
    @GetMapping("/{userId}/public-profile")
    fun getPublicProfile(
        @AuthenticationPrincipal principal: UserDetails?,
        @PathVariable userId: Long,
    ): ResponseEntity<PublicUserProfileResponse> {
        val viewerId = (principal as? UserPrincipal)?.id
            ?: throw UnauthorizedException("authenticated user required")
        return ResponseEntity.ok(userPublicProfileService.getPublicProfile(viewerId, userId))
    }
}
