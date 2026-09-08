package com.goldpet.domain.course.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.course.dto.CourseLikeStatusResponse
import com.goldpet.domain.course.service.CourseLikeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Course Like", description = "코스 좋아요 API")
@RestController
@RequestMapping("/api/v1/courses/{courseId}/likes")
class CourseLikeController(
    private val courseLikeService: CourseLikeService
) {
    @Operation(summary = "좋아요 토글")
    @PostMapping
    fun toggleLike(
        @PathVariable courseId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CourseLikeStatusResponse> {
        return ResponseEntity.ok(courseLikeService.toggleLike(courseId, principal.id))
    }

    @Operation(summary = "내 좋아요 상태 조회")
    @GetMapping("/status")
    fun getLikeStatus(
        @PathVariable courseId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CourseLikeStatusResponse> {
        return ResponseEntity.ok(courseLikeService.getLikeStatus(courseId, principal.id))
    }
}
