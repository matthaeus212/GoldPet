package com.goldpet.domain.auth.dto

data class VerificationRequest(
    val phoneNumber: String
)

data class VerificationConfirmRequest(
    val phoneNumber: String,
    val code: String
)

data class VerificationResponse(
    val success: Boolean,
    val message: String,
    val code: String? = null
)
