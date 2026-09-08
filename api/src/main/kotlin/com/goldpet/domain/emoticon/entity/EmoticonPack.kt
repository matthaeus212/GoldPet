package com.goldpet.domain.emoticon.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*

@Entity
@Table(name = "emoticon_packs")
class EmoticonPack(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "price_gold", nullable = false)
    var priceGold: Int = 0,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
) : BaseTimeEntity()
