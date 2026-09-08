package com.goldpet.domain.walk.dto

import com.goldpet.common.annotation.PublicFacingDto
import com.goldpet.domain.walk.entity.Walk
import com.goldpet.domain.walk.entity.WalkSpotType
import com.goldpet.domain.walk.service.PhotoUrlSigner
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LineString
import java.time.LocalDateTime

data class CreateWalkRequest(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    @field:PositiveOrZero(message = "Distance must be >= 0")
    val distanceKm: Double,
    @field:Positive(message = "Duration must be > 0")
    val durationSeconds: Long,
    @field:Size(min = 2, message = "Path must have at least 2 points")
    val path: List<List<Double>>, // List of [latitude, longitude] pairs
    val spots: List<WalkSpotDto> = emptyList(),
    val caloriesBurned: Double?,
    val notes: String?,
    val petIds: List<Long> = emptyList(),
    val isPublic: Boolean = true,
    val followedCourseId: Long? = null,
) {
    fun toLineString(): LineString {
        val geometryFactory = GeometryFactory(PrecisionModel(), 4326)
        val coords = path.map { Coordinate(it[1], it[0]) }.toTypedArray() // JTS expects (longitude, latitude)
        return geometryFactory.createLineString(coords)
    }
}

data class WalkSpotDto(
    val id: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val type: WalkSpotType,
    val timestamp: LocalDateTime,
    val imageUrl: String?,
    val imageKey: String? = null,
    val imageUrlViewer: String? = null,
    // iOS WKWebView 메모리 누적 완화 — 딥링크 콜드오픈 진입사진도 600px medium 우선 사용하도록 노출.
    val imageUrlMedium: String? = null,
    val imageUrlThumb: String? = null,
    val imageKeyViewer: String? = null,
    val imageKeyThumb: String? = null,
    val note: String?,
    val hiddenFromPublic: Boolean = false
)

data class WalkPhotoResponse(
    val id: Long,
    val imageUrl: String?,
    val imageKey: String?,
    val imageUrlViewer: String? = null,
    // 600px medium 변형 서명 URL — 스와이프 표시 기본값(iOS WKWebView 메모리 누적 완화)
    val imageUrlMedium: String? = null,
    val imageUrlThumb: String? = null,
    val imageKeyViewer: String? = null,
    val imageKeyThumb: String? = null,
    val note: String?,
    val walkDate: LocalDateTime,
    val walkId: Long,
    val userId: Long,
    val petName: String?,
    val petImageUrl: String?,
    val hiddenFromPublic: Boolean = false
)

data class PhotoMintResponse(
    val spotId: Long,
    val imageUrl: String?,
    val imageKey: String?,
    val imageUrlViewer: String? = null,
    // 600px medium 변형 서명 URL — 스와이프 표시 기본값(iOS WKWebView 메모리 누적 완화)
    val imageUrlMedium: String? = null,
    val imageUrlThumb: String? = null,
    val imageKeyViewer: String? = null,
    val imageKeyThumb: String? = null,
    val expiresAt: LocalDateTime
)

data class UpdateSpotNoteRequest(
    val note: String?
)

data class UpdateSpotVisibilityRequest(
    val hiddenFromPublic: Boolean
)

data class WalkResponse(
    val id: Long,
    val userId: Long,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val distanceKm: Double,
    val durationSeconds: Long,
    val path: List<List<Double>>, // List of [latitude, longitude] pairs
    val spots: List<WalkSpotDto>,
    val caloriesBurned: Double?,
    val notes: String?,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
    val petNames: List<String> = emptyList(),
    val petIds: List<Long> = emptyList(),
    val petProfileImageUrls: List<String?> = emptyList(),
    val isPublic: Boolean = true,
    val userNickname: String? = null,
    val userProfileImageUrl: String? = null,
    val hasPath: Boolean = false,
) {
    companion object {
        fun from(walk: Walk, photoUrlSigner: PhotoUrlSigner? = null): WalkResponse {
            val pathCoordinates = walk.path.coordinates.map { listOf(it.y, it.x) } // Convert back to [latitude, longitude]
            return WalkResponse(
                id = walk.id,
                userId = walk.user.id,
                startTime = walk.startTime,
                endTime = walk.endTime,
                distanceKm = walk.distanceKm,
                durationSeconds = walk.durationSeconds,
                path = pathCoordinates,
                spots = walk.spots.map { spot ->
                    val rawKey = spot.imageUrl
                    val signedOriginal = photoUrlSigner?.signedUrlOrNull(rawKey)
                    WalkSpotDto(
                        id = spot.id,
                        latitude = spot.location.y,
                        longitude = spot.location.x,
                        type = spot.type,
                        timestamp = spot.timestamp,
                        imageUrl = signedOriginal,
                        imageKey = rawKey,
                        imageUrlViewer = photoUrlSigner?.signedViewerUrlOrNull(spot.imageKeyViewer),
                        imageUrlMedium = photoUrlSigner?.signedMediumUrlOrNull(spot.imageKeyMedium),
                        imageUrlThumb = photoUrlSigner?.signedThumbUrlOrNull(spot.imageKeyThumb),
                        imageKeyViewer = spot.imageKeyViewer,
                        imageKeyThumb = spot.imageKeyThumb,
                        note = spot.note,
                        hiddenFromPublic = spot.hiddenFromPublic
                    )
                },
                caloriesBurned = walk.caloriesBurned,
                notes = walk.notes,
                startAddress = walk.startAddress,
                endAddress = walk.endAddress,
                startLatitude = walk.startLocation?.y,
                startLongitude = walk.startLocation?.x,
                endLatitude = walk.endLocation?.y,
                endLongitude = walk.endLocation?.x,
                petNames = walk.walkPets.map { it.pet.name },
                petIds = walk.walkPets.map { it.pet.id },
                petProfileImageUrls = walk.walkPets.map { it.pet.profileImageUrl },
                isPublic = walk.isPublic,
                userNickname = walk.user.nickname,
                userProfileImageUrl = walk.user.profileImageUrl,
                hasPath = walk.path.coordinates.size >= 2,
            )
        }

        fun publicFrom(walk: Walk, photoUrlSigner: PhotoUrlSigner? = null): WalkResponse {
            val pathCoordinates = walk.path.coordinates.map { listOf(it.y, it.x) }
            return WalkResponse(
                id = walk.id,
                userId = walk.user.id,
                startTime = walk.startTime,
                endTime = walk.endTime,
                distanceKm = walk.distanceKm,
                durationSeconds = walk.durationSeconds,
                path = pathCoordinates,
                spots = walk.spots
                    .filter { !it.hiddenFromPublic }
                    .map { spot ->
                        val rawKey = spot.imageUrl
                        val signedOriginal = photoUrlSigner?.signedUrlOrNull(rawKey)
                        WalkSpotDto(
                            id = spot.id,
                            latitude = spot.location.y,
                            longitude = spot.location.x,
                            type = spot.type,
                            timestamp = spot.timestamp,
                            imageUrl = signedOriginal,
                            imageKey = rawKey,
                            imageUrlViewer = photoUrlSigner?.signedViewerUrlOrNull(spot.imageKeyViewer),
                            imageUrlMedium = photoUrlSigner?.signedMediumUrlOrNull(spot.imageKeyMedium),
                            imageUrlThumb = photoUrlSigner?.signedThumbUrlOrNull(spot.imageKeyThumb),
                            imageKeyViewer = spot.imageKeyViewer,
                            imageKeyThumb = spot.imageKeyThumb,
                            note = spot.note,
                            hiddenFromPublic = false
                        )
                    },
                caloriesBurned = walk.caloriesBurned,
                notes = null,
                startAddress = walk.startAddress,
                endAddress = walk.endAddress,
                startLatitude = walk.startLocation?.y,
                startLongitude = walk.startLocation?.x,
                endLatitude = walk.endLocation?.y,
                endLongitude = walk.endLocation?.x,
                petNames = walk.walkPets.map { it.pet.name },
                petIds = walk.walkPets.map { it.pet.id },
                petProfileImageUrls = walk.walkPets.map { it.pet.profileImageUrl },
                isPublic = walk.isPublic,
                userNickname = walk.user.nickname,
                userProfileImageUrl = walk.user.profileImageUrl,
                hasPath = walk.path.coordinates.size >= 2,
            )
        }
    }
}

data class WalkStatsResponse(
    val totalDistanceKm: Double,
    val totalDurationMinutes: Long,
    val totalCalories: Double,
    val totalGoldEarned: Int,
    val totalWalks: Long
)

/**
 * community-author-profile-gallery Phase 2 F1 — 특정 유저의 공개 walk 사진 cursor pagination 응답.
 *
 * Phase 1 `AuthorPostsPage(posts, nextCursor)` 와 대칭 구조. 실제 사진 DTO 는 기존 `WalkPhotoResponse` 재사용.
 */
@PublicFacingDto
data class UserWalkPhotosPage(
    val photos: List<WalkPhotoResponse>,
    val nextCursor: String?,
)
