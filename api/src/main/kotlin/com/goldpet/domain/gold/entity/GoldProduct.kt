package com.goldpet.domain.gold.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*

@Entity
@Table(name = "gold_products")
class GoldProduct(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "product_code", nullable = false, unique = true)
    val productCode: String,

    @Column(nullable = false)
    var name: String,

    @Column(name = "gold_amount", nullable = false)
    var goldAmount: Int,

    @Column(nullable = false)
    var price: Int,

    @Column(nullable = false)
    var bonus: Int = 0,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(name = "product_type", nullable = false)
    var productType: String = "ONE_TIME",

    @Column(name = "duration_months")
    var durationMonths: Int? = null,

    @Column(name = "discount_percent", nullable = false)
    var discountPercent: Int = 0,

    @Column(name = "monthly_price")
    var monthlyPrice: Int? = null
) : BaseTimeEntity()
