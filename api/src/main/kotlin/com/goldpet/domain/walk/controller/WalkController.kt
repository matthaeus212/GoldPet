package com.goldpet.domain.walk.controller

import com.goldpet.domain.walk.dto.CreateWalkRequest
import com.goldpet.domain.walk.dto.PhotoMintResponse
import com.goldpet.domain.walk.dto.WalkPhotoResponse
import com.goldpet.domain.walk.dto.WalkResponse
import com.goldpet.domain.walk.dto.BoundingBoxRequest
import com.goldpet.domain.walk.dto.WalkRankingResponse
import com.goldpet.domain.walk.dto.WalkCoupleRankingResponse
import com.goldpet.domain.walk.dto.WalkStatsResponse
import com.goldpet.domain.walk.dto.UpdateSpotNoteRequest
import com.goldpet.domain.walk.dto.UpdateSpotVisibilityRequest
import com.goldpet.domain.walk.service.WalkService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "Walk", description = "산책 기록 및 통계 API")
@RestController
@RequestMapping("/api/v1/walks")
class WalkController(
    private val walkService: WalkService
) {

    @Operation(summary = "산책 기록 생성")
    @PostMapping
    fun createWalk(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal?,
        @RequestBody @Valid request: CreateWalkRequest
    ): ResponseEntity<WalkResponse> {
        if (userDetails == null) return ResponseEntity.status(401).build()
        val walkResponse = walkService.createWalk(userDetails.id, request)
        return ResponseEntity.created(URI.create("/api/v1/walks/${walkResponse.id}")).body(walkResponse)
    }

    @Operation(summary = "산책 기록 조회")
    @GetMapping("/{walkId}")
    fun getWalk(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal?,
        @PathVariable walkId: Long
    ): ResponseEntity<WalkResponse> {
        val walkResponse = walkService.getWalk(walkId, userDetails?.id)
        return ResponseEntity.ok(walkResponse)
    }

    @Operation(summary = "공개 산책 목록 조회")
    @GetMapping("/public")
    fun getPublicWalks(
        @org.springframework.data.web.PageableDefault(size = 10) pageable: org.springframework.data.domain.Pageable,
        @RequestParam(required = false) yearMonth: String?,
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) province: String?
    ): ResponseEntity<org.springframework.data.domain.Page<WalkResponse>> {
        val walkResponses = walkService.getPublicWalks(pageable, yearMonth, userId, province)
        return ResponseEntity.ok(walkResponses)
    }

    @Operation(summary = "내 산책 통계 조회")
    @GetMapping("/my/stats")
    fun getMyStats(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal?
    ): ResponseEntity<WalkStatsResponse> {
        if (userDetails == null) return ResponseEntity.status(401).build()
        val stats = walkService.getMyStats(userDetails.id)
        return ResponseEntity.ok(stats)
    }

    @Operation(summary = "내 산책 목록 조회")
    @GetMapping("/my")
    fun getMyWalks(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal?,
        @org.springframework.data.web.PageableDefault(size = 10) pageable: org.springframework.data.domain.Pageable,
        @RequestParam(required = false) yearMonth: String?
    ): ResponseEntity<org.springframework.data.domain.Page<WalkResponse>> {
        if (userDetails == null) return ResponseEntity.status(401).build()
        val walkResponses = walkService.getWalksByUser(userDetails.id, pageable, yearMonth)
        return ResponseEntity.ok(walkResponses)
    }

    @Operation(summary = "영역 내 산책 검색")
    @GetMapping("/search")
    fun searchWalksInArea(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal?,
        @ModelAttribute @Valid request: BoundingBoxRequest
    ): ResponseEntity<List<WalkResponse>> {
        val walkResponses = walkService.findWalksInArea(request)
        return ResponseEntity.ok(walkResponses)
    }

    @Operation(summary = "내 산책 사진 목록 조회")
    @GetMapping("/my/photos")
    fun getMyPhotos(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal?,
        @org.springframework.data.web.PageableDefault(size = 20) pageable: org.springframework.data.domain.Pageable,
        @RequestParam(required = false) yearMonth: String?
    ): ResponseEntity<org.springframework.data.domain.Page<WalkPhotoResponse>> {
        if (userDetails == null) return ResponseEntity.status(401).build()
        val photos = walkService.getMyPhotos(userDetails.id, yearMonth, pageable)
        return ResponseEntity.ok(photos)
    }

    @Operation(summary = "산책 스팟 메모 수정")
    @PatchMapping("/{walkId}/spots/{spotId}")
    fun updateSpotNote(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal?,
        @PathVariable walkId: Long,
        @PathVariable spotId: Long,
        @RequestBody request: UpdateSpotNoteRequest
    ): ResponseEntity<WalkPhotoResponse> {
        if (userDetails == null) return ResponseEntity.status(401).build()
        val photo = walkService.updateSpotNote(userDetails.id, walkId, spotId, request.note)
        return ResponseEntity.ok(photo)
    }

    @Operation(summary = "산책 스팟 공개 여부 변경")
    @PatchMapping("/{walkId}/spots/{spotId}/visibility")
    fun updateSpotVisibility(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal?,
        @PathVariable walkId: Long,
        @PathVariable spotId: Long,
        @RequestBody request: UpdateSpotVisibilityRequest
    ): ResponseEntity<WalkPhotoResponse> {
        if (userDetails == null) return ResponseEntity.status(401).build()
        val photo = walkService.updateSpotVisibility(userDetails.id, walkId, spotId, request.hiddenFromPublic)
        return ResponseEntity.ok(photo)
    }

    @Operation(summary = "친구 산책 사진 조회")
    @GetMapping("/public/photos")
    fun getPublicPhotos(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal?,
        @org.springframework.data.web.PageableDefault(size = 20) pageable: org.springframework.data.domain.Pageable
    ): ResponseEntity<org.springframework.data.domain.Page<WalkPhotoResponse>> {
        if (userDetails == null) return ResponseEntity.status(401).build()
        val photos = walkService.getPublicPhotos(userDetails.id, pageable)
        return ResponseEntity.ok(photos)
    }

    @Operation(summary = "산책 사진 URL 발급")
    @GetMapping("/photos/{spotId}/url")
    fun mintPhotoUrl(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal?,
        @PathVariable spotId: Long
    ): ResponseEntity<PhotoMintResponse> {
        if (userDetails == null) return ResponseEntity.status(401).build()
        val response = walkService.mintPhotoUrl(spotId, userDetails.id)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "산책 랭킹 조회 (weekly|monthly)")
    @GetMapping("/ranking")
    fun getWalkRanking(
        @RequestParam(required = false) period: String?,
        @RequestParam(required = false) province: String?,
        @org.springframework.data.web.PageableDefault(size = 50) pageable: org.springframework.data.domain.Pageable
    ): ResponseEntity<List<WalkRankingResponse>> {
        val effectivePeriod = period ?: "weekly"
        val rankings = walkService.getWalkRanking(effectivePeriod, pageable, province)
        return ResponseEntity.ok(rankings)
    }

    @Operation(summary = "월별(달력) 산책 랭킹 조회")
    @GetMapping("/ranking/calendar")
    fun getCalendarWalkRanking(
        @RequestParam yearMonth: String,
        @RequestParam(required = false) province: String?,
        @org.springframework.data.web.PageableDefault(size = 50) pageable: org.springframework.data.domain.Pageable
    ): ResponseEntity<WalkCoupleRankingResponse> {
        val result = walkService.getCalendarRanking(yearMonth, pageable, province)
        return ResponseEntity.ok(result)
    }
}
