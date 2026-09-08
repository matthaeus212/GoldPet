package com.goldpet.domain.pet.repository

import com.goldpet.domain.pet.entity.PetProfileImage
import org.springframework.data.jpa.repository.JpaRepository

interface PetProfileImageRepository : JpaRepository<PetProfileImage, Long>
