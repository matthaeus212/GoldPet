package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.service.AdminCourseService
import com.goldpet.domain.course.entity.CourseDifficulty
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

// DTOs
data class CourseListItemResponse(
    val id: Long,
    val title: String,
    val authorNickname: String,
    val authorId: Long,
    val difficulty: String,
    val region: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val likeCount: Int,
    val commentCount: Int,
    val walkCount: Int,
    val rating: Double,
    val ratingCount: Int,
    val isHidden: Boolean,
    val createdAt: String?
)

data class CourseDetailAdminResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val authorId: Long,
    val authorNickname: String,
    val difficulty: String,
    val region: String,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val likeCount: Int,
    val commentCount: Int,
    val walkCount: Int,
    val rating: Double,
    val ratingCount: Int,
    val isHidden: Boolean,
    val thumbnailUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val spots: List<CourseSpotAdminResponse>,
    val comments: List<CourseCommentAdminResponse>
)

data class CourseSpotAdminResponse(
    val id: Long,
    val type: String,
    val name: String?,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val orderIndex: Int
)

data class CourseCommentAdminResponse(
    val id: Long,
    val content: String,
    val rating: Int?,
    val authorId: Long,
    val authorNickname: String,
    val likeCount: Int,
    val isHidden: Boolean,
    val createdAt: String?
)

@Tag(name = "Admin Course", description = "관리자 코스 관리 API")
@RestController
@RequestMapping("/api/v1/admin/courses")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminCourseController(
    private val courseService: AdminCourseService
) {

    @Operation(summary = "코스 목록 조회")
    @GetMapping
    fun getCourses(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) difficulty: CourseDifficulty?,
        @RequestParam(required = false) isPublished: Boolean?,
        @RequestParam(defaultValue = "created_at") sortBy: String,
        @RequestParam(defaultValue = "desc") sortDir: String
    ): ResponseEntity<Page<CourseListItemResponse>> {
        val sort = if (sortDir == "asc") Sort.by(sortBy).ascending() else Sort.by(sortBy).descending()
        val pageable = PageRequest.of(page, size, sort)
        return ResponseEntity.ok(courseService.getCourses(pageable, search, difficulty, isPublished))
    }

    @Operation(summary = "코스 상세 조회")
    @GetMapping("/{courseId}")
    fun getCourseDetail(@PathVariable courseId: Long): ResponseEntity<CourseDetailAdminResponse> {
        return ResponseEntity.ok(courseService.getCourseDetail(courseId))
    }

    @Operation(summary = "코스 숨김 처리")
    @PostMapping("/{courseId}/hide")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun hideCourse(@PathVariable courseId: Long): ResponseEntity<Void> {
        courseService.hideCourse(courseId)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "코스 숨김 해제")
    @PostMapping("/{courseId}/unhide")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun unhideCourse(@PathVariable courseId: Long): ResponseEntity<Void> {
        courseService.unhideCourse(courseId)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "코스 삭제")
    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteCourse(@PathVariable courseId: Long): ResponseEntity<Void> {
        courseService.deleteCourse(courseId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "댓글 숨김 처리")
    @PostMapping("/comments/{commentId}/hide")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun hideComment(@PathVariable commentId: Long): ResponseEntity<Void> {
        courseService.hideComment(commentId)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "댓글 숨김 해제")
    @PostMapping("/comments/{commentId}/unhide")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun unhideComment(@PathVariable commentId: Long): ResponseEntity<Void> {
        courseService.unhideComment(commentId)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "댓글 삭제")
    @DeleteMapping("/comments/{commentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteComment(@PathVariable commentId: Long): ResponseEntity<Void> {
        courseService.deleteComment(commentId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "코스 썸네일 일괄 재생성 (Google Maps 마이그레이션용)")
    @PostMapping("/regenerate-thumbnails")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun regenerateThumbnails(): ResponseEntity<Map<String, Any>> {
        val result = courseService.regenerateAllThumbnails()
        return ResponseEntity.ok(result)
    }
}
