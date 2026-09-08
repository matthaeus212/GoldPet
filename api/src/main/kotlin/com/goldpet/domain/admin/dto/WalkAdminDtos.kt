package com.goldpet.domain.admin.dto

import com.goldpet.domain.walk.entity.Walk
import com.goldpet.domain.walk.entity.WalkSpot
import com.goldpet.domain.walk.service.PhotoUrlSigner
import java.time.LocalDate
import java.time.LocalDateTime

data class WalkAdminResponse(
    val id: Long,
    val userId: Long,
    val userNickname: String?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val distanceKm: Double,
    val durationSeconds: Long,
    val caloriesBurned: Double?,
    val notes: String?,
    val createdAt: LocalDateTime?
) {
    companion object {
        fun from(walk: Walk): WalkAdminResponse = WalkAdminResponse(
            id = walk.id,
            userId = walk.user.id,
            userNickname = walk.user.nickname,
            startTime = walk.startTime,
            endTime = walk.endTime,
            distanceKm = walk.distanceKm,
            durationSeconds = walk.durationSeconds,
            caloriesBurned = walk.caloriesBurned,
            notes = walk.notes,
            createdAt = walk.createdAt
        )
    }
}

data class WalkDetailPetInfo(
    val petId: Long,
    val petName: String,
    val species: String?,
    val breed: String?
)

data class CoordinateDto(
    val latitude: Double,
    val longitude: Double
)

data class WalkSpotAdminDto(
    val id: Long,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: LocalDateTime,
    val imageUrl: String?,
    val imageKey: String?,
    val note: String?
) {
    companion object {
        fun from(spot: WalkSpot, photoUrlSigner: PhotoUrlSigner? = null): WalkSpotAdminDto = WalkSpotAdminDto(
            id = spot.id,
            type = spot.type.name,
            latitude = spot.location.y,
            longitude = spot.location.x,
            timestamp = spot.timestamp,
            imageUrl = photoUrlSigner?.signedUrlOrNull(spot.imageUrl) ?: spot.imageUrl,
            imageKey = spot.imageUrl,
            note = spot.note
        )
    }
}

data class WalkDetailResponse(
    val id: Long,
    val userId: Long,
    val userNickname: String?,
    val userProfileImageUrl: String?,
    val userPets: List<WalkDetailPetInfo>?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val distanceKm: Double,
    val durationSeconds: Long,
    val caloriesBurned: Double?,
    val notes: String?,
    val pathCoordinates: List<CoordinateDto>,
    val spots: List<WalkSpotAdminDto>,
    val createdAt: LocalDateTime?
)

data class WalkDeleteRequest(
    val reason: String
)

data class DailyStatEntry(
    val date: LocalDate,
    val count: Int,
    val totalDistanceKm: Double
)

data class WalkStatsDetailResponse(
    val totalWalks: Long,
    val totalDistanceKm: Double,
    val averageDistanceKm: Double,
    val averageDurationMinutes: Double,
    val dailyBreakdown: List<DailyStatEntry>
)

data class WalkRankingAdminResponse(
    val rank: Int,
    val userId: Long,
    val nickname: String?,
    val profileImageUrl: String?,
    val petId: Long?,
    val petName: String?,
    val petProfileImageUrl: String?,
    val totalDistanceKm: Double,
    val totalMinutes: Long,
    val totalGold: Int,
    val walkCount: Int
)
