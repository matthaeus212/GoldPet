package com.goldpet.domain.walk.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.walk.service.WalkService
import com.goldpet.infra.GeocodingService
import com.goldpet.infra.StaticMapService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.concurrent.TimeUnit

@Tag(name = "Map", description = "지도 및 지오코딩 API")
@RestController
@RequestMapping("/api/v1/maps")
class MapController(
    private val geocodingService: GeocodingService,
    private val staticMapService: StaticMapService,
    private val walkService: WalkService
) {
    @Operation(summary = "역지오코딩 (좌표 → 주소)")
    @GetMapping("/reverse-geocode")
    fun reverseGeocode(
        @RequestParam lat: Double,
        @RequestParam lng: Double
    ): ResponseEntity<Map<String, String?>> {
        val result = geocodingService.reverseGeocodeStructured(lat, lng)
        return ResponseEntity.ok(mapOf("address" to result.fullAddress, "province" to result.province))
    }

    @Operation(summary = "지오코딩 (주소 → 좌표)")
    @GetMapping("/geocode")
    fun geocode(
        @RequestParam address: String
    ): ResponseEntity<Map<String, List<GeocodingService.GeocodeResult>>> {
        val results = geocodingService.geocode(address)
        return ResponseEntity.ok(mapOf("results" to results))
    }

    @Operation(summary = "산책 경로 정적 지도 이미지 조회")
    @GetMapping("/static/{walkId}")
    fun getStaticMap(
        @AuthenticationPrincipal userDetails: UserPrincipal?,
        @PathVariable walkId: Long,
        @RequestParam(defaultValue = "120") w: Int,
        @RequestParam(defaultValue = "120") h: Int
    ): ResponseEntity<ByteArray> {
        if (userDetails == null) {
            return ResponseEntity.status(401).build()
        }

        // ARCH-005: 조회+인가는 서비스가 판단하고, HTTP 상태 매핑만 여기서 한다.
        val coordinates = when (val result = walkService.getMapPath(walkId, userDetails.id)) {
            is WalkService.MapPath.NotFound -> return ResponseEntity.notFound().build()
            is WalkService.MapPath.Forbidden -> return ResponseEntity.status(403).build()
            is WalkService.MapPath.Ok -> result.coordinates
        }
        if (coordinates.size < 2) {
            return ResponseEntity.noContent().build()
        }

        // Clamp dimensions to reasonable range
        val width = w.coerceIn(60, 600)
        val height = h.coerceIn(60, 600)

        val imageBytes = staticMapService.getStaticMapImage(coordinates, width, height)
            ?: return ResponseEntity.status(502).build()

        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
            .body(imageBytes)
    }
}
