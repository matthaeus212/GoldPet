package com.goldpet.domain.course.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.course.dto.CourseCommentResponse
import com.goldpet.domain.course.dto.CreateCourseCommentRequest
import com.goldpet.domain.course.dto.UpdateCourseCommentRequest
import com.goldpet.domain.course.service.CourseCommentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "Course Comment", description = "코스 댓글 API")
@RestController
@RequestMapping("/api/v1/courses/{courseId}/comments")
class CourseCommentController(
    private val courseCommentService: CourseCommentService
) {
    @Operation(summary = "댓글 목록 조회")
    @GetMapping
    fun getComments(
        @PathVariable courseId: Long,
        @PageableDefault(size = 50) pageable: Pageable
    ): ResponseEntity<Page<CourseCommentResponse>> {
        return ResponseEntity.ok(courseCommentService.getComments(courseId, pageable))
    }

    @Operation(summary = "댓글 작성")
    @PostMapping
    fun createComment(
        @PathVariable courseId: Long,
        @RequestBody @Valid request: CreateCourseCommentRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CourseCommentResponse> {
        val response = courseCommentService.createComment(courseId, principal.id, request)
        return ResponseEntity.created(URI.create("/api/v1/courses/$courseId/comments/${response.id}")).body(response)
    }

    @Operation(summary = "댓글 수정")
    @PutMapping("/{commentId}")
    fun updateComment(
        @PathVariable courseId: Long,
        @PathVariable commentId: Long,
        @RequestBody @Valid request: UpdateCourseCommentRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CourseCommentResponse> {
        return ResponseEntity.ok(courseCommentService.updateComment(commentId, principal.id, request))
    }

    @Operation(summary = "댓글 삭제")
    @DeleteMapping("/{commentId}")
    fun deleteComment(
        @PathVariable courseId: Long,
        @PathVariable commentId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        courseCommentService.deleteComment(commentId, principal.id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "댓글 좋아요 토글")
    @PostMapping("/{commentId}/like")
    fun toggleCommentLike(
        @PathVariable courseId: Long,
        @PathVariable commentId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Boolean> {
        return ResponseEntity.ok(courseCommentService.toggleCommentLike(commentId, principal.id))
    }
}
