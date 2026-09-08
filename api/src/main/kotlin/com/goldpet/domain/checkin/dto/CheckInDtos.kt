package com.goldpet.domain.checkin.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldpet.domain.checkin.entity.CheckIn
import com.goldpet.domain.checkin.entity.Place
import com.goldpet.domain.checkin.entity.PlaceCategory
import java.time.LocalDateTime

data class PlaceResponse(
    val id: Long,
    val name: String,
    val category: PlaceCategory,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val description: String?,
    val imageUrl: String?,
    @get:JsonProperty("isVerified")
    val isVerified: Boolean,
    val checkinCount: Int
) {
    companion object {
        fun from(place: Place) = PlaceResponse(
            id = place.id,
            name = place.name,
            category = place.category,
            address = place.address,
            latitude = place.locationGeom.y,
            longitude = place.locationGeom.x,
            description = place.description,
            imageUrl = place.imageUrl,
            isVerified = place.isVerified,
            checkinCount = place.checkinCount
        )
    }
}

data class CreatePlaceRequest(
    val name: String,
    val category: PlaceCategory,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val description: String?
)

data class CheckInResponse(
    val id: Long,
    val userId: Long,
    val userNickname: String?,
    val userProfileImage: String?,
    val placeId: Long,
    val placeName: String,
    val photoUrl: String?,
    val memo: String?,
    val createdAt: LocalDateTime?
) {
    companion object {
        fun from(checkIn: CheckIn) = CheckInResponse(
            id = checkIn.id,
            userId = checkIn.user.id,
            userNickname = checkIn.user.nickname,
            userProfileImage = checkIn.user.profileImageUrl,
            placeId = checkIn.place.id,
            placeName = checkIn.place.name,
            photoUrl = checkIn.photoUrl,
            memo = checkIn.memo,
            createdAt = checkIn.createdAt
        )
    }
}

data class CreateCheckInRequest(
    val placeId: Long,
    val photoUrl: String? = null,
    val memo: String? = null
)

data class NearbyPlaceRequest(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = 1000.0
)
