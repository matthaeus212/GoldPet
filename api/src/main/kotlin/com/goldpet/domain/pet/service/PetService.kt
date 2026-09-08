package com.goldpet.domain.pet.service

import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.pet.dto.CreatePetRequest
import com.goldpet.domain.pet.dto.PetResponse
import com.goldpet.domain.pet.dto.SpeciesResponse
import com.goldpet.domain.pet.dto.BreedResponse
import com.goldpet.domain.pet.dto.UpdatePetRequest
import com.goldpet.domain.pet.entity.Pet
import com.goldpet.domain.pet.entity.PetProfileImage
import com.goldpet.domain.pet.repository.PetBreedRepository
import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.gamification.service.BadgeAwardService
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.pet.repository.PetSpeciesRepository
import com.goldpet.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PetService(
    private val petRepository: PetRepository,
    private val userRepository: UserRepository,
    private val petSpeciesRepository: PetSpeciesRepository,
    private val petBreedRepository: PetBreedRepository,
    private val petAttributeRepository: com.goldpet.domain.pet.repository.PetAttributeRepository,
    private val badgeAwardService: BadgeAwardService,
    private val fileAttachmentLookupService: FileAttachmentLookupService
) {
    private val log = LoggerFactory.getLogger(PetService::class.java)

    @Transactional
    fun createPet(userId: Long, request: CreatePetRequest): PetResponse {
        val owner = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val species = petSpeciesRepository.findById(request.speciesId).orElseThrow { NotFoundException("PetSpecies not found") }
        val breed = request.breedId?.let { petBreedRepository.findById(it).orElseThrow { NotFoundException("PetBreed not found") } }

        val pet = Pet(
            owner = owner,
            name = request.name,
            species = species,
            breed = breed,
            gender = request.gender,
            birthDate = request.birthDate,
            weightKg = request.weightKg,
            isNeutered = request.isNeutered,
            profileImageUrl = request.profileImageUrls?.firstOrNull() ?: request.profileImageUrl,
            temperamentTags = request.temperamentTags
        )

        request.profileImageUrls?.forEachIndexed { index, url ->
            pet.profileImages.add(PetProfileImage(pet = pet, imageUrl = url, orderIndex = index))
        }

        val savedPet = petRepository.save(pet)

        if (request.profileImageUrls.isNullOrEmpty() && savedPet.profileImageUrl != null) {
            savedPet.profileImages.add(PetProfileImage(pet = savedPet, imageUrl = savedPet.profileImageUrl!!, orderIndex = 0))
            petRepository.save(savedPet)
        }

        try {
            badgeAwardService.checkAndAwardBadges(userId, BadgeConditionType.PET_REGISTER)
        } catch (e: Exception) {
            log.warn("Badge check failed for userId={}", userId, e)
        }

        return PetResponse.from(savedPet, fileAttachmentLookupService)
    }

    fun getPet(petId: Long): PetResponse {
        val pet = petRepository.findById(petId).orElseThrow { NotFoundException("Pet not found") }
        return PetResponse.from(pet, fileAttachmentLookupService)
    }

    fun getPetsByOwner(userId: Long): List<PetResponse> {
        val pets = petRepository.findByOwnerId(userId)
        // T1-1.2 N+1 방지: 리스트 경로는 variant lookup 을 배치로 warm 한 뒤 DTO 매핑.
        val urls = pets.flatMap { pet ->
            listOfNotNull(pet.profileImageUrl) + pet.profileImages.map { it.imageUrl }
        }
        fileAttachmentLookupService.batchLookup(urls)
        return pets.map { PetResponse.from(it, fileAttachmentLookupService) }
    }

    @Cacheable("pet:species")
    fun getAllSpecies(): List<SpeciesResponse> {
        return petSpeciesRepository.findAll().map { species ->
            SpeciesResponse.from(species, petBreedRepository.countByPetSpeciesId(species.id))
        }
    }

    @Cacheable("breeds", key = "#speciesId")
    fun getBreedsBySpecies(speciesId: Int): List<BreedResponse> {
        return petBreedRepository.findAllByPetSpeciesId(speciesId).map { BreedResponse.from(it) }
    }

    @Cacheable("pet_attributes", key = "'all'")
    fun getPetAttributes(): List<com.goldpet.domain.admin.dto.PetAttributeResponse> {
        return petAttributeRepository.findAllByOrderByDisplayOrderAsc()
            .map { com.goldpet.domain.admin.dto.PetAttributeResponse.from(it) }
    }



    @Transactional
    fun updatePet(userId: Long, petId: Long, request: UpdatePetRequest): PetResponse {
        val pet = petRepository.findById(petId).orElseThrow { NotFoundException("Pet not found") }
        if (pet.owner.id != userId) throw ForbiddenException("Not authorized to update this pet")

        request.name?.let { pet.name = it }
        request.speciesId?.let {
            pet.species = petSpeciesRepository.findById(it).orElseThrow { NotFoundException("PetSpecies not found") }
        }
        request.breedId?.let {
            pet.breed = petBreedRepository.findById(it).orElseThrow { NotFoundException("PetBreed not found") }
        }
        request.gender?.let { pet.gender = it }
        request.birthDate?.let { pet.birthDate = it }
        request.weightKg?.let { pet.weightKg = it }
        request.isNeutered?.let { pet.isNeutered = it }
        request.profileImageUrl?.let {
            pet.profileImageUrl = it
            if (pet.profileImages.isEmpty()) {
                pet.profileImages.add(PetProfileImage(pet = pet, imageUrl = it, orderIndex = 0))
            } else {
                pet.profileImages[0].imageUrl = it
            }
        }
        request.profileImageUrls?.let { urls ->
            pet.profileImages.clear()
            urls.forEachIndexed { index, url ->
                pet.profileImages.add(PetProfileImage(pet = pet, imageUrl = url, orderIndex = index))
            }
            pet.profileImageUrl = urls.firstOrNull()
        }
        request.temperamentTags?.let { pet.temperamentTags = it }

        val updatedPet = petRepository.save(pet)
        return PetResponse.from(updatedPet, fileAttachmentLookupService)
    }

    @Transactional
    fun deletePet(userId: Long, petId: Long) {
        val pet = petRepository.findById(petId).orElseThrow { NotFoundException("Pet not found") }
        if (pet.owner.id != userId) throw ForbiddenException("Not authorized to delete this pet")
        petRepository.delete(pet)
    }
}
