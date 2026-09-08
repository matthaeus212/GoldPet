package com.goldpet.domain.user.dto

import com.goldpet.domain.user.entity.User

data class FriendLocationResponse(
    val id: Long,
    val nickname: String?,
    val profileImageUrl: String?,
    val latitude: Double?,
    val longitude: Double?,
    val distance: Double? = null
) {
    companion object {
        fun from(user: User, distance: Double? = null): FriendLocationResponse {
            // Grid-snap to ~1.1km precision (0.01 degree) to protect precise location
            val snappedLat = user.mainLocationGeom?.y?.let { Math.round(it * 100.0) / 100.0 }
            val snappedLng = user.mainLocationGeom?.x?.let { Math.round(it * 100.0) / 100.0 }
            return FriendLocationResponse(
                id = user.id,
                nickname = user.nickname,
                profileImageUrl = user.profileImageUrl,
                latitude = snappedLat,
                longitude = snappedLng,
                distance = distance
            )
        }
    }
}
