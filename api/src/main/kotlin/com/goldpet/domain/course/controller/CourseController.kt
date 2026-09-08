package com.goldpet.domain.course.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.course.dto.CourseResponse
import com.goldpet.domain.course.dto.CreateCourseRequest
import com.goldpet.domain.course.dto.UpdateCourseRequest
import com.goldpet.domain.course.entity.CourseDifficulty
import com.goldpet.domain.course.service.CourseService
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

@Tag(name = "Course", description = "산책 코스 API")
@RestController
@RequestMapping("/api/v1/courses")
class CourseController(
    private val courseService: CourseService
) {
    @Operation(summary = "코스 생성")
    @PostMapping
    fun createCourse(
        @RequestBody @Valid request: CreateCourseRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CourseResponse> {
        val response = courseService.createCourse(principal.id, request)
        return ResponseEntity.created(URI.create("/api/v1/courses/${response.id}")).body(response)
    }

    @Operation(summary = "코스 상세 조회")
    @GetMapping("/{courseId}")
    fun getCourse(
        @PathVariable courseId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): ResponseEntity<CourseResponse> {
        return ResponseEntity.ok(courseService.getCourse(courseId, principal?.id))
    }

    @Operation(summary = "코스 수정")
    @PutMapping("/{courseId}")
    fun updateCourse(
        @PathVariable courseId: Long,
        @RequestBody @Valid request: UpdateCourseRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CourseResponse> {
        return ResponseEntity.ok(courseService.updateCourse(courseId, principal.id, request))
    }

    @Operation(summary = "코스 삭제")
    @DeleteMapping("/{courseId}")
    fun deleteCourse(
        @PathVariable courseId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        courseService.deleteCourse(courseId, principal.id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "근처 코스 검색")
    @GetMapping("/search")
    fun searchCourses(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(required = false, defaultValue = "2000") radiusMeters: Double,
        @RequestParam(required = false) difficulty: CourseDifficulty?,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<CourseResponse>> {
        return ResponseEntity.ok(courseService.searchNearby(lat, lng, radiusMeters, difficulty, pageable))
    }

    @Operation(summary = "인기 코스 목록")
    @GetMapping("/popular")
    fun getPopularCourses(
        @RequestParam(required = false) region: String?,
        @RequestParam(required = false) difficulty: CourseDifficulty?,
        @RequestParam(defaultValue = "popular") sortBy: String,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<CourseResponse>> {
        return ResponseEntity.ok(courseService.getPopularCourses(region, difficulty, sortBy, pageable))
    }

    @Operation(summary = "내가 만든 코스 목록")
    @GetMapping("/my")
    fun getMyCourses(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Page<CourseResponse>> {
        return ResponseEntity.ok(courseService.getMyCourses(principal.id, pageable))
    }

    @Operation(summary = "산책 기록으로 코스 생성")
    @PostMapping("/from-walk/{walkId}")
    fun createCourseFromWalk(
        @PathVariable walkId: Long,
        @RequestBody @Valid request: CreateCourseRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<CourseResponse> {
        val response = courseService.createFromWalk(principal.id, walkId, request)
        return ResponseEntity.created(URI.create("/api/v1/courses/${response.id}")).body(response)
    }
}
