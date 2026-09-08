package com.goldpet.domain.admin.dto

import com.goldpet.domain.pet.entity.AttributeCategory
import com.goldpet.domain.pet.entity.AttributeOption
import com.goldpet.domain.pet.entity.InputType
import com.goldpet.domain.pet.entity.PetAttribute

data class PetAttributeRequest(
    val category: AttributeCategory,
    val code: String,
    val name: String,
    val inputType: InputType,
    val displayOrder: Int,
    val options: List<AttributeOption> = emptyList()
)

data class PetAttributeResponse(
    val id: Long,
    val category: AttributeCategory,
    val code: String,
    val name: String,
    val inputType: InputType,
    val displayOrder: Int,
    val options: List<AttributeOption>
) {
    companion object {
        fun from(entity: PetAttribute) = PetAttributeResponse(
            id = entity.id,
            category = entity.category,
            code = entity.code,
            name = entity.name,
            inputType = entity.inputType,
            displayOrder = entity.displayOrder,
            options = entity.options ?: emptyList()
        )
    }
}
