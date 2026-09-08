package com.goldpet.domain.user.dto

import com.goldpet.domain.user.entity.DeviceType

data class RegisterDeviceRequest(
    val deviceId: String,
    val fcmToken: String? = null,
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val deviceName: String? = null,
    val appVersion: String? = null
)

data class UserDeviceResponse(
    val id: Long,
    val deviceId: String,
    val deviceType: DeviceType,
    val deviceName: String?,
    val appVersion: String?,
    val lastLoginAt: String
)
