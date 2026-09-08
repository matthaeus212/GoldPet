package com.goldpet.domain.aiprofile.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "ai_styles")
class AIStyle(
    @Id
    val id: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(length = 500)
    var description: String? = null,

    @Column(name = "preview_url", length = 500)
    var previewUrl: String? = null,

    @Column(name = "gold_cost", nullable = false)
    var goldCost: Int = 10,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(name = "preset_id", length = 100)
    var presetId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
