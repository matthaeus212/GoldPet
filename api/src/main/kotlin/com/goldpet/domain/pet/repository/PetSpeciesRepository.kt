package com.goldpet.domain.pet.repository

import com.goldpet.domain.pet.entity.PetSpecies
import org.springframework.data.jpa.repository.JpaRepository

interface PetSpeciesRepository : JpaRepository<PetSpecies, Int>
