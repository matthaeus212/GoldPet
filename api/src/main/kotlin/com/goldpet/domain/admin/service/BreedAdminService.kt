package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.dto.CreateBreedRequest
import com.goldpet.domain.admin.dto.UpdateBreedRequest
import com.goldpet.domain.pet.dto.BreedResponse
import com.goldpet.domain.pet.entity.PetBreed
import com.goldpet.domain.pet.repository.PetBreedRepository
import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.pet.repository.PetSpeciesRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BreedAdminService(
    private val petBreedRepository: PetBreedRepository,
    private val petSpeciesRepository: PetSpeciesRepository
) {

    @Cacheable(value = ["breeds"], key = "'all'")
    fun getAllBreeds(): List<BreedResponse> {
        return petBreedRepository.findAll().map { it.toResponse() }
    }

    @Cacheable(value = ["breeds"], key = "#speciesId")
    fun getBreedsBySpecies(speciesId: Int): List<BreedResponse> {
        return petBreedRepository.findAllByPetSpeciesId(speciesId).map { it.toResponse() }
    }

    fun getBreed(breedId: Int): BreedResponse {
        val breed = petBreedRepository.findById(breedId)
            .orElseThrow { NotFoundException("품종을 찾을 수 없습니다.") }
        return breed.toResponse()
    }

    @Transactional
    @CacheEvict(value = ["breeds"], allEntries = true)
    fun createBreed(request: CreateBreedRequest): BreedResponse {
        val species = petSpeciesRepository.findById(request.speciesId)
            .orElseThrow { NotFoundException("종을 찾을 수 없습니다.") }

        val breed = PetBreed(
            petSpecies = species,
            name = request.name,
            category = request.category,
            description = request.description
        )
        val saved = petBreedRepository.save(breed)
        return saved.toResponse()
    }

    @Transactional
    @CacheEvict(value = ["breeds"], allEntries = true)
    fun updateBreed(breedId: Int, request: UpdateBreedRequest): BreedResponse {
        val breed = petBreedRepository.findById(breedId)
            .orElseThrow { NotFoundException("품종을 찾을 수 없습니다.") }

        breed.update(request.name, request.category, request.description)
        val saved = petBreedRepository.save(breed)
        return saved.toResponse()
    }

    @Transactional
    @CacheEvict(value = ["breeds"], allEntries = true)
    fun deleteBreed(breedId: Int) {
        if (!petBreedRepository.existsById(breedId)) {
            throw NotFoundException("품종을 찾을 수 없습니다.")
        }
        petBreedRepository.deleteById(breedId)
    }

    private fun PetBreed.toResponse() = BreedResponse(
        id = this.id,
        speciesId = this.petSpecies.id,
        name = this.name,
        category = this.category,
        description = this.description
    )
}
