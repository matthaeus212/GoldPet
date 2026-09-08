package com.goldpet.domain.pet.repository

import com.goldpet.domain.pet.entity.AttributeCategory
import com.goldpet.domain.pet.entity.PetAttribute
import org.springframework.data.jpa.repository.JpaRepository

interface PetAttributeRepository : JpaRepository<PetAttribute, Long> {
    fun findAllByOrderByDisplayOrderAsc(): List<PetAttribute>
    fun findAllByCategoryOrderByDisplayOrderAsc(category: AttributeCategory): List<PetAttribute>
}
