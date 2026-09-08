package com.goldpet.domain.walk.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin

data class BoundingBoxRequest(
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val minLat: Double,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val minLon: Double,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val maxLat: Double,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val maxLon: Double
)
