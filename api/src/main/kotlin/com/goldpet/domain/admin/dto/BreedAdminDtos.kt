package com.goldpet.domain.admin.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class CreateBreedRequest(
    @field:Positive(message = "Species ID must be positive")
    val speciesId: Int,
    @field:NotBlank(message = "Name is required")
    val name: String,
    val category: String? = null,
    val description: String? = null
)

data class UpdateBreedRequest(
    val name: String,
    val category: String? = null,
    val description: String? = null
)
