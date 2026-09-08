package com.goldpet.domain.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class SnsSignupRequest(
    @field:NotBlank(message = "Provider is required")
    val provider: String,

    @field:NotBlank(message = "Nickname is required")
    @field:Pattern(regexp = "^[a-zA-Z0-9가-힣]{2,10}$", message = "Nickname must be 2-10 characters long and contain only Korean, English, or numbers")
    val nickname: String,

    val name: String?,

    // Apple Guideline 5.1.1(v): demographic fields must be optional, not required.
    val birthDate: String?,

    val gender: String?,

    val phoneNumber: String?
)
