package com.goldpet.domain.friend.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.friend.dto.FriendResponse
import com.goldpet.domain.friend.service.FriendService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Friend", description = "친구 찾기 API")
@RestController
@RequestMapping("/api/v1/friends")
class FriendController(
    private val friendService: FriendService,
    private val userService: com.goldpet.domain.user.service.UserService
) {
    @Operation(summary = "친구 찾기", description = "주변의 친구를 검색합니다.")
    @GetMapping
    fun getFriends(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) distance: String?, // e.g., "1km", "3km"
        @RequestParam(required = false) sort: String?, // "registered", "popular", "distance", "compatible"
        @RequestParam(required = false) petTypes: List<String>?,
        @RequestParam(required = false) genders: List<String>?,
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<FriendResponse>> {
        // Parse distance string "1km" -> 1.0 (Double)
        val distanceKm = distance?.replace("km", "")?.toDoubleOrNull()
        
        // Try to use provided lat/lng, otherwise lookup user's saved location
        var userLat = lat ?: 0.0
        var userLng = lng ?: 0.0

        if (userLat == 0.0 && userLng == 0.0) {
            // ARCH-005: 리포지토리 직접 조회 → 서비스 계약 (저장된 메인 위치)
            val (lat0, lng0) = userService.getMainCoordinates(principal.id)
            userLat = lat0
            userLng = lng0
        }

        // If still no location, default to Seoul center (same as HomeService)
        if (userLat == 0.0 && userLng == 0.0) {
            userLat = 37.5665
            userLng = 126.978
        }

        return ResponseEntity.ok(
            friendService.getFriends(
                myUserId = principal.id,
                myLat = userLat,
                myLng = userLng,
                distanceKm = distanceKm,
                sortBy = sort,
                petTypes = petTypes,
                genders = genders,
                pageable = pageable
            )
        )
    }
}
