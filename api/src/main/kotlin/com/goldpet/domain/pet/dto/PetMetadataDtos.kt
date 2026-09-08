package com.goldpet.domain.pet.dto

import com.goldpet.domain.pet.entity.PetBreed
import com.goldpet.domain.pet.entity.PetSpecies

data class SpeciesResponse(
    val id: Int,
    val code: String,
    val name: String,
    val description: String? = null,
    val breedCount: Long = 0
) {
    companion object {
        fun from(entity: PetSpecies, breedCount: Long = 0) = SpeciesResponse(
            id = entity.id,
            code = entity.code,
            name = entity.name,
            description = entity.description,
            breedCount = breedCount
        )
    }
}

data class BreedResponse(
    val id: Int,
    val speciesId: Int,
    val name: String,
    val category: String? = null,
    val description: String? = null
) {
    companion object {
        fun from(entity: PetBreed) = BreedResponse(
            id = entity.id,
            speciesId = entity.petSpecies.id,
            name = entity.name,
            category = entity.category,
            description = entity.description
        )
    }
}
