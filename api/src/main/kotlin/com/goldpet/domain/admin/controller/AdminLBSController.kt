package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.service.AdminLBSService
import com.goldpet.domain.checkin.entity.PlaceCategory
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin LBS Management", description = "관리자 산책/지도 관리 API")
@RestController
@RequestMapping("/api/v1/admin/lbs")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminLBSController(
    private val adminLBSService: AdminLBSService
) {
    @Operation(summary = "산책 통계 조회")
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<LBSStatsResponse> {
        return ResponseEntity.ok(adminLBSService.getStats())
    }

    @Operation(summary = "장소 카테고리 목록 조회")
    @GetMapping("/place-categories")
    fun getPlaceCategories(): ResponseEntity<List<PlaceCategoryResponse>> {
        return ResponseEntity.ok(PlaceCategory.entries.map { PlaceCategoryResponse(value = it.name, displayName = it.displayName) })
    }

    @Operation(summary = "장소 목록 조회")
    @GetMapping("/places")
    fun getPlaces(
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ResponseEntity<Any> {
        if (page == null && size == null) {
            return ResponseEntity.ok(adminLBSService.getPlaces())
        }
        val pageable = org.springframework.data.domain.PageRequest.of(
            page ?: 0,
            (size ?: 20).coerceIn(1, 100),
            org.springframework.data.domain.Sort.by("id").descending(),
        )
        return ResponseEntity.ok(adminLBSService.getPlaces(pageable))
    }

    @Operation(summary = "장소 추가")
    @PostMapping("/places")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createPlace(@RequestBody request: CreatePlaceRequest): ResponseEntity<PlaceAdminResponse> {
        return ResponseEntity.ok(adminLBSService.createPlace(request))
    }

    @Operation(summary = "장소 수정")
    @PutMapping("/places/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun updatePlace(
        @PathVariable id: Long,
        @RequestBody request: UpdatePlaceRequest
    ): ResponseEntity<PlaceAdminResponse> {
        return ResponseEntity.ok(adminLBSService.updatePlace(id, request))
    }

    @Operation(summary = "장소 삭제")
    @DeleteMapping("/places/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun deletePlace(@PathVariable id: Long): ResponseEntity<Void> {
        adminLBSService.deletePlace(id)
        return ResponseEntity.noContent().build()
    }
}

data class LBSStatsResponse(
    val totalWalks: Long,
    val todayWalks: Long,
    val totalDistance: Double,
    val avgDuration: Int,
    val topSpots: List<TopSpotResponse>
)

data class TopSpotResponse(
    val id: Long,
    val name: String,
    val visits: Int
)

data class PlaceAdminResponse(
    val id: Long,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val visits: Int,
    val rating: Double
)

data class CreatePlaceRequest(
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double
)

data class UpdatePlaceRequest(
    val name: String? = null,
    val category: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class PlaceCategoryResponse(
    val value: String,
    val displayName: String
)
