package com.goldpet.domain.friend.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class FriendResponse(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
    /** T1-1.4b: owner main profile thumbnail (200px). */
    val profileImageUrlThumbnail: String? = null,
    /** T1-1.4b: owner main profile viewer (1600px). */
    val profileImageUrlViewer: String? = null,
    val profileImages: List<String>,  // Owner's all profile images (multiple)
    /** T1-1.4b: owner profile images thumbnail list. */
    val profileImagesThumbnail: List<String>? = null,
    /** T1-1.4b: owner profile images viewer list. */
    val profileImagesViewer: List<String>? = null,
    /** T2: WebP thumbnail for primary owner profile image. */
    val profileImageUrlThumbnailWebp: String? = null,
    /** T2: WebP thumbnail list for owner profile images. */
    val profileImagesThumbnailWebp: List<String?>? = null,
    val ownerGender: String?,         // "MALE" | "FEMALE"
    val ownerAge: Int?,               // calculated from birthDate
    val ownerIntro: String?,          // self introduction
    val ownerMbti: String?,           // MBTI
    val ownerInterests: List<String>, // interests names
    val ownerHobbies: List<String>,   // hobbies names
    val petName: String,
    val petBreed: String,
    val petAge: Int,
    val petGender: String, // "MALE" | "FEMALE"
    @get:JsonProperty("isNeutered")
    val isNeutered: Boolean,
    val onWalking: Boolean,
    val description: String,
    val tags: List<String>,
    val images: List<String>,
    /** T1-1.4b: pet images thumbnail list. */
    val imagesThumbnail: List<String>? = null,
    /** T1-1.4b: pet images viewer list. */
    val imagesViewer: List<String>? = null,
    /** T2: WebP thumbnail list for pet images. */
    val imagesThumbnailWebp: List<String?>? = null,
    val distance: Double, // in meters
    val status: String, // "online", "offline", "walking"
    @Deprecated("Location coordinates removed for privacy. Use locationText and distance instead.")
    val location: LocationDto?,
    val locationText: String?
)

data class LocationDto(
    val lat: Double,
    val lng: Double
)
