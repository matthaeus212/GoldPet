package com.goldpet.domain.common.dto

import com.goldpet.domain.common.entity.AppNoticeType
import java.time.LocalDateTime

data class AppNoticeResponse(
    val id: Long,
    val type: AppNoticeType,
    val title: String,
    val content: String?,
    val imageUrls: List<String>,
    val linkUrl: String?,
    val targetScreen: String?,
    val priority: Int,
    val isDismissible: Boolean,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime?,
    val isActive: Boolean = true
)

data class CreateAppNoticeRequest(
    val type: AppNoticeType,
    val title: String,
    val content: String? = null,
    val imageUrls: List<String>? = null,
    val linkUrl: String? = null,
    val targetScreen: String? = "ALL",
    val priority: Int = 0,
    val isDismissible: Boolean = true,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime? = null
)

data class UpdateAppNoticeRequest(
    val type: AppNoticeType? = null,
    val title: String? = null,
    val content: String? = null,
    val imageUrls: List<String>? = null,
    val linkUrl: String? = null,
    val targetScreen: String? = null,
    val priority: Int? = null,
    val isDismissible: Boolean? = null,
    val isActive: Boolean? = null,
    val startAt: LocalDateTime? = null,
    val endAt: LocalDateTime? = null
)
