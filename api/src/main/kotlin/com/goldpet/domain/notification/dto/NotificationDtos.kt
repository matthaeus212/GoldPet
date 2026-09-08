package com.goldpet.domain.notification.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldpet.domain.notification.entity.Notification
import com.goldpet.domain.notification.entity.NotificationType
import java.time.LocalDateTime

data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val message: String,
    val targetId: Long?,
    val targetType: String?,
    @get:JsonProperty("isRead")
    val isRead: Boolean,
    val senderId: Long?,
    val senderNickname: String?,
    val senderProfileImage: String?,
    val createdAt: LocalDateTime?
) {
    companion object {
        fun from(notification: Notification) = NotificationResponse(
            id = notification.id,
            type = notification.type,
            title = notification.title,
            message = notification.message,
            targetId = notification.targetId,
            targetType = notification.targetType,
            isRead = notification.isRead,
            senderId = notification.senderId,
            senderNickname = notification.senderNickname,
            senderProfileImage = notification.senderProfileImage,
            createdAt = notification.createdAt
        )
    }
}

data class CreateNotificationRequest(
    val userId: Long,
    val type: NotificationType,
    val title: String,
    val message: String,
    val targetId: Long? = null,
    val targetType: String? = null,
    val senderId: Long? = null
)

data class UnreadCountResponse(
    val count: Long
)
