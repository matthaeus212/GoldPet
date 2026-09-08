package com.goldpet.domain.pet.repository

import com.goldpet.domain.pet.entity.PetBreed
import org.springframework.data.jpa.repository.JpaRepository

interface PetBreedRepository : JpaRepository<PetBreed, Int> {
    fun findAllByPetSpeciesId(speciesId: Int): List<PetBreed>
    fun countByPetSpeciesId(speciesId: Int): Long
}
