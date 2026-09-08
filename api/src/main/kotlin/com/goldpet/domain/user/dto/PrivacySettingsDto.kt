package com.goldpet.domain.user.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class PrivacySettingsRequest(
    val isLocationSharingEnabled: Boolean? = null,
    val isProfilePublic: Boolean? = null
)

data class PrivacySettingsResponse(
    @get:JsonProperty("isLocationSharingEnabled")
    val isLocationSharingEnabled: Boolean,
    @get:JsonProperty("isProfilePublic")
    val isProfilePublic: Boolean
)
