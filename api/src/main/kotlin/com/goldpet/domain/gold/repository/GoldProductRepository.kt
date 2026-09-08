package com.goldpet.domain.gold.repository

import com.goldpet.domain.gold.entity.GoldProduct
import org.springframework.data.jpa.repository.JpaRepository

interface GoldProductRepository : JpaRepository<GoldProduct, Long> {
    fun findAllByIsActiveTrueOrderByDisplayOrderAsc(): List<GoldProduct>
    fun findByProductCode(productCode: String): GoldProduct?
}
