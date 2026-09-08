package com.goldpet.domain.community.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.common.exception.UnauthorizedException
import com.goldpet.domain.community.dto.AuthorPostsPage
import com.goldpet.domain.community.service.CommunityAuthorPostsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * community-author-profile-gallery §4-1 — 작성자별 공개 게시글 cursor pagination.
 *
 * `UserProfilePage` 그리드 infinite-scroll 에서 호출. task #4 에서 `SecurityConfig` 의
 * `/api/v1/users/{userId}/community/posts` 경로가 `authenticated()` 로 잠긴다.
 */
@Tag(name = "Community", description = "커뮤니티 API")
@RestController
@RequestMapping("/api/v1/users")
class CommunityAuthorPostsController(
    private val communityAuthorPostsService: CommunityAuthorPostsService,
) {
    @Operation(summary = "작성자 공개 게시글 cursor 페이징")
    @GetMapping("/{userId}/community/posts")
    fun getAuthorPosts(
        @AuthenticationPrincipal principal: UserDetails?,
        @PathVariable userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "30") size: Int,
    ): ResponseEntity<AuthorPostsPage> {
        val viewerId = (principal as? UserPrincipal)?.id
            ?: throw UnauthorizedException("authenticated user required")
        val page = communityAuthorPostsService.findByAuthorId(
            viewerId = viewerId,
            authorId = userId,
            cursor = cursor,
            size = size,
        )
        return ResponseEntity.ok(page)
    }
}
