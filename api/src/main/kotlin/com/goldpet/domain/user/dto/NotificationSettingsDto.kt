package com.goldpet.domain.user.dto

import com.goldpet.domain.user.entity.User

data class NotificationSettingsRequest(
    val pushAlert: Boolean,
    val chatAlert: Boolean,
    val communityAlert: Boolean,
    val marketingAlert: Boolean,
    // 산책 리마인더(재참여 넛지) 동의 — W2c. 누락 시 기존 동의 유지를 위해 default true.
    val reengagementAlert: Boolean = true,
)

data class NotificationSettingsResponse(
    val pushAlert: Boolean,
    val chatAlert: Boolean,
    val communityAlert: Boolean,
    val marketingAlert: Boolean,
    val reengagementAlert: Boolean,
) {
    companion object {
        fun from(user: User) = NotificationSettingsResponse(
            pushAlert = user.isNotificationEnabled,
            chatAlert = user.isChatAlertEnabled,
            communityAlert = user.isCommunityAlertEnabled,
            marketingAlert = user.isMarketingAlertEnabled,
            reengagementAlert = user.isReengagementAlertEnabled,
        )
    }
}
