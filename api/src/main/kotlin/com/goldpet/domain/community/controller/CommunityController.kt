package com.goldpet.domain.community.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.community.dto.CommunityCategoryResponse
import com.goldpet.domain.community.dto.CommunityCommentCreateRequest
import com.goldpet.domain.community.dto.CommunityCommentResponse
import com.goldpet.domain.community.dto.CommunityCommentUpdateRequest
import com.goldpet.domain.community.dto.CommunityPostCreateRequest
import com.goldpet.domain.community.dto.CommunityPostResponse
import com.goldpet.domain.community.dto.CommunityPostUpdateRequest
import com.goldpet.domain.community.service.CommunityService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "Community", description = "커뮤니티 API")
@RestController
@RequestMapping("/api/v1/community")
class CommunityController(
    private val communityService: CommunityService
) {
    @Operation(summary = "카테고리 목록 조회")
    @GetMapping("/categories")
    fun getCategories(): ResponseEntity<List<CommunityCategoryResponse>> {
        return ResponseEntity.ok(communityService.getCategories())
    }

    @Operation(summary = "게시글 목록 조회 (sort=trending 시 시간감쇠 인기순)")
    @GetMapping("/posts")
    fun getPosts(
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) sort: String?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): ResponseEntity<Page<CommunityPostResponse>> {
        val result = if (sort.equals("trending", ignoreCase = true) || sort.equals("hot", ignoreCase = true)) {
            communityService.getTrendingPosts(categoryId, pageable, principal?.id)
        } else {
            communityService.getPosts(categoryId, pageable, principal?.id)
        }
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "게시글 상세 조회")
    @GetMapping("/posts/{postId}")
    fun getPostDetail(
        @PathVariable postId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): ResponseEntity<CommunityPostResponse> {
        return ResponseEntity.ok(communityService.getPostDetail(postId, principal?.id))
    }

    @Operation(summary = "내가 쓴 글 목록")
    @GetMapping("/posts/me")
    fun getMyPosts(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Page<CommunityPostResponse>> {
        return ResponseEntity.ok(communityService.getMyPosts(principal.id, pageable))
    }

    @Operation(summary = "내가 쓴 댓글 목록")
    @GetMapping("/comments/me")
    fun getMyComments(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Page<CommunityCommentResponse>> {
        return ResponseEntity.ok(communityService.getMyComments(principal.id, pageable))
    }

    @Operation(summary = "게시글 검색")
    @GetMapping("/posts/search")
    fun searchPosts(
        @RequestParam keyword: String,
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): ResponseEntity<Page<CommunityPostResponse>> {
        return ResponseEntity.ok(communityService.searchPosts(keyword, pageable, principal?.id))
    }

    @Operation(summary = "게시글 작성")
    @PostMapping("/posts")
    fun createPost(
        @RequestBody @Valid request: CommunityPostCreateRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CommunityPostResponse> {
        val response = communityService.createPost(principal.id, request)
        return ResponseEntity.created(URI.create("/v1/community/posts/${response.id}")).body(response)
    }

    @Operation(summary = "게시글 수정")
    @PutMapping("/posts/{postId}")
    fun updatePost(
        @PathVariable postId: Long,
        @RequestBody @Valid request: CommunityPostUpdateRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CommunityPostResponse> {
        return ResponseEntity.ok(communityService.updatePost(postId, principal.id, request))
    }

    @Operation(summary = "게시글 삭제")
    @DeleteMapping("/posts/{postId}")
    fun deletePost(
        @PathVariable postId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        communityService.deletePost(postId, principal.id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "좋아요 토글")
    @PostMapping("/posts/{postId}/like")
    fun toggleLike(
        @PathVariable postId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Boolean> {
        val isLiked = communityService.toggleLike(principal.id, postId)
        return ResponseEntity.ok(isLiked)
    }

    @Operation(summary = "댓글 목록 조회")
    @GetMapping("/posts/{postId}/comments")
    fun getComments(
        @PathVariable postId: Long,
        @PageableDefault(size = 50) pageable: Pageable
    ): ResponseEntity<Page<CommunityCommentResponse>> {
        return ResponseEntity.ok(communityService.getComments(postId, pageable))
    }

    @Operation(summary = "댓글 작성")
    @PostMapping("/posts/{postId}/comments")
    fun createComment(
        @PathVariable postId: Long,
        @RequestBody @Valid request: CommunityCommentCreateRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CommunityCommentResponse> {
        val response = communityService.createComment(principal.id, postId, request)
        return ResponseEntity.created(URI.create("/v1/community/comments/${response.id}")).body(response)
    }

    @Operation(summary = "댓글 좋아요 토글")
    @PostMapping("/comments/{commentId}/like")
    fun toggleCommentLike(
        @PathVariable commentId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Boolean> {
        val isLiked = communityService.toggleCommentLike(principal.id, commentId)
        return ResponseEntity.ok(isLiked)
    }

    @Operation(summary = "댓글 수정")
    @PutMapping("/comments/{commentId}")
    fun updateComment(
        @PathVariable commentId: Long,
        @RequestBody request: CommunityCommentUpdateRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CommunityCommentResponse> {
        return ResponseEntity.ok(communityService.updateComment(commentId, principal.id, request.content))
    }

    @Operation(summary = "댓글 삭제")
    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(
        @PathVariable commentId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        communityService.deleteComment(commentId, principal.id)
        return ResponseEntity.noContent().build()
    }
}
