package com.goldpet.domain.admin.dto

import java.time.LocalDateTime

data class CacheInfoResponse(
    val name: String,
    val ttlMinutes: Long,
    val description: String,
    val lastCleared: LocalDateTime?
)

data class CacheClearRequest(
    val cacheName: String
)

data class CacheClearResult(
    val cacheName: String,
    val success: Boolean,
    val message: String?
)

data class CacheClearLogResponse(
    val id: Long,
    val cacheName: String,
    val clearedBy: String,
    val clearedAt: LocalDateTime,
    val reason: String?
)
