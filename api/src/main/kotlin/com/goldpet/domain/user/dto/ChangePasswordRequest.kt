package com.goldpet.domain.user.dto

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)
