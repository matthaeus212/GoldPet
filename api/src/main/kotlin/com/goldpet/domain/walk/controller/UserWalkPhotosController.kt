package com.goldpet.domain.walk.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.walk.dto.UserWalkPhotosPage
import com.goldpet.domain.walk.service.WalkService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * community-author-profile-gallery Phase 2 F1 — 유저 공개 프로필의 "산책" 탭 백엔드.
 *
 * Phase 1 의 `UserPublicProfileController` / `CommunityAuthorPostsController` 와 동일 원칙:
 *  - `authenticated()` 강제 (SecurityConfig matcher).
 *  - `UserProfileRateLimitFilter` 의 PROTECTED_PATTERNS 대상 경로.
 *  - cursor base64url `{epochMs}:{id}` pagination, size 1..100 clamp default 30.
 */
@Tag(name = "User Walk Photos", description = "특정 유저의 공개 walk 사진 갤러리")
@RestController
@RequestMapping("/api/v1/users")
class UserWalkPhotosController(
    private val walkService: WalkService,
) {

    @Operation(summary = "유저 공개 walk 사진 갤러리")
    @GetMapping("/{userId}/walks/photos")
    fun getUserWalkPhotos(
        @PathVariable userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "30") size: Int,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<UserWalkPhotosPage> {
        val result = walkService.getUserWalkPhotos(
            viewerId = principal.id,
            targetUserId = userId,
            cursor = cursor,
            size = size,
        )
        return ResponseEntity.ok(result)
    }
}
