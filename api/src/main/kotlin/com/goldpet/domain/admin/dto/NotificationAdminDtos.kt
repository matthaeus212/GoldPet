package com.goldpet.domain.admin.dto

import java.time.LocalDateTime

data class NotificationTemplateRequest(
    val title: String,
    val body: String,
    val category: String
)

data class NotificationTemplateResponse(
    val id: Long,
    val title: String,
    val body: String,
    val category: String,
    val createdAt: LocalDateTime
)

data class SendNotificationRequest(
    val templateId: Long?,
    val title: String?,
    val body: String?,
    val targetType: String,
    val targetUserIds: List<Long>?
)

data class SendNotificationResult(
    val successCount: Int,
    val failCount: Int
)

data class DeliveryLogResponse(
    val id: Long,
    val title: String,
    val targetType: String,
    val sentCount: Int,
    val sentAt: LocalDateTime
)
