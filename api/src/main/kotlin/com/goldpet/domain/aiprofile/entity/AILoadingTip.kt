package com.goldpet.domain.aiprofile.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*

@Entity
@Table(name = "ai_loading_tips")
class AILoadingTip(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(nullable = false, length = 200)
    var content: String,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
) : BaseTimeEntity()
