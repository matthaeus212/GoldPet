package com.goldpet.domain.user.dto

data class UpdateNotificationRequest(
    val enabled: Boolean
)

data class UpdateFcmTokenRequest(
    val fcmToken: String?
)
