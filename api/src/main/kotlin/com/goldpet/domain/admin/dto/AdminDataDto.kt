package com.goldpet.domain.admin.dto

import jakarta.validation.constraints.NotBlank

data class CreateSpeciesRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val code: String? = null,
    val description: String? = null
)

data class CreateCategoryRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val code: String? = null
)

data class CreateInterestRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val orderIndex: Int = 0
)

data class CreateHobbyRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    val orderIndex: Int = 0
)
