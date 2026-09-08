package com.goldpet.domain.checkin.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.checkin.dto.*
import com.goldpet.domain.checkin.entity.PlaceCategory
import com.goldpet.domain.checkin.service.CheckInService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "CheckIn", description = "체크인 API")
@RestController
@RequestMapping("/api/v1/checkin")
class CheckInController(
    private val checkInService: CheckInService
) {
    // Places
    @Operation(summary = "주변 장소 찾기")
    @GetMapping("/places/nearby")
    fun getNearbyPlaces(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(defaultValue = "1000") radiusMeters: Double
    ): ResponseEntity<List<PlaceResponse>> {
        val request = NearbyPlaceRequest(latitude, longitude, radiusMeters)
        return ResponseEntity.ok(checkInService.getNearbyPlaces(request))
    }

    @Operation(summary = "장소 목록")
    @GetMapping("/places")
    fun getPlaces(
        @RequestParam(required = false) category: PlaceCategory?,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PlaceResponse>> {
        return ResponseEntity.ok(checkInService.getPlaces(category, pageable))
    }

    @Operation(summary = "장소 상세")
    @GetMapping("/places/{placeId}")
    fun getPlace(@PathVariable placeId: Long): ResponseEntity<PlaceResponse> {
        return ResponseEntity.ok(checkInService.getPlace(placeId))
    }

    @Operation(summary = "장소 검색")
    @GetMapping("/places/search")
    fun searchPlaces(
        @RequestParam name: String,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PlaceResponse>> {
        return ResponseEntity.ok(checkInService.searchPlaces(name, pageable))
    }

    @Operation(summary = "장소 등록")
    @PostMapping("/places")
    fun createPlace(@RequestBody request: CreatePlaceRequest): ResponseEntity<PlaceResponse> {
        val response = checkInService.createPlace(request)
        return ResponseEntity.created(URI.create("/v1/checkin/places/${response.id}")).body(response)
    }

    // Check-ins
    @Operation(summary = "체크인하기")
    @PostMapping
    fun checkIn(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateCheckInRequest
    ): ResponseEntity<CheckInResponse> {
        val response = checkInService.checkIn(principal.id, request)
        return ResponseEntity.created(URI.create("/v1/checkin/${response.id}")).body(response)
    }

    @Operation(summary = "내 체크인 목록")
    @GetMapping("/my")
    fun getMyCheckIns(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<CheckInResponse>> {
        return ResponseEntity.ok(checkInService.getMyCheckIns(principal.id, pageable))
    }

    @Operation(summary = "장소별 체크인 목록")
    @GetMapping("/places/{placeId}/checkins")
    fun getPlaceCheckIns(
        @PathVariable placeId: Long,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<CheckInResponse>> {
        return ResponseEntity.ok(checkInService.getPlaceCheckIns(placeId, pageable))
    }

    @Operation(summary = "30일간 체크인 횟수")
    @GetMapping("/count")
    fun getCheckInCount(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<Map<String, Long>> {
        val count = checkInService.getCheckInCount(principal.id)
        return ResponseEntity.ok(mapOf("count" to count))
    }
}
