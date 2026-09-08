package com.goldpet.domain.admin.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class BannerResponse(
    val id: Long,
    val title: String,
    val imageUrl: String?,
    /** T1-1.5: banner image thumbnail variant (200px). */
    val imageUrlThumbnail: String? = null,
    /** T1-1.5: banner image viewer variant (1600px). */
    val imageUrlViewer: String? = null,
    val link: String?,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val displayOrder: Int,
    val placement: String
)

data class BannerCreateRequest(
    val title: String,
    val imageUrl: String? = null,
    val link: String? = null,
    val placement: String
)

data class BannerUpdateRequest(
    val title: String,
    val imageUrl: String? = null,
    val link: String? = null,
    val isActive: Boolean = true,
    val displayOrder: Int = 0,
    val placement: String
)

data class CampaignResponse(
    val id: Long,
    val name: String,
    val startDate: String,
    val endDate: String,
    val status: String,
    val participants: Int
)

data class CampaignCreateRequest(
    val name: String,
    val startDate: String,
    val endDate: String
)
