package com.goldpet.domain.user.dto

data class ProfileImageUpdateRequest(
    val profileImageUrl: String?,
    val profileImageUrls: List<String>? = null
)
