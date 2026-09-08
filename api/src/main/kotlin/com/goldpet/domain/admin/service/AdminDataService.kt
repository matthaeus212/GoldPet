package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.dto.*
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.community.repository.CommunityCategoryRepository
import com.goldpet.domain.pet.entity.PetBreed
import com.goldpet.domain.pet.entity.PetSpecies
import com.goldpet.domain.pet.repository.PetBreedRepository
import com.goldpet.domain.pet.repository.PetSpeciesRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminDataService(
    private val speciesRepository: PetSpeciesRepository,
    private val breedRepository: PetBreedRepository,
    private val categoryRepository: CommunityCategoryRepository,
    private val interestRepository: com.goldpet.domain.user.repository.InterestRepository,
    private val hobbyRepository: com.goldpet.domain.user.repository.HobbyRepository
) {
    fun getSpecies(): List<Map<String, Any>> {
        return speciesRepository.findAll().map { species ->
            mapOf(
                "id" to species.id,
                "code" to species.code,
                "name" to species.name,
                "description" to (species.description ?: ""),
                "breedCount" to breedRepository.countByPetSpeciesId(species.id)
            )
        }
    }

    @Transactional
    @CacheEvict(value = ["pet:species"], allEntries = true)
    fun createSpecies(request: CreateSpeciesRequest): Map<String, Any> {
        val name = request.name
        val code = request.code ?: name.uppercase().replace(" ", "_")
        val description = request.description
        val species = PetSpecies(code = code, name = name, description = description)
        val saved = speciesRepository.save(species)
        return mapOf("id" to saved.id, "code" to saved.code, "name" to saved.name, "breedCount" to 0)
    }

    @Transactional
    @CacheEvict(value = ["pet:species"], allEntries = true)
    fun deleteSpecies(id: Long) {
        speciesRepository.deleteById(id.toInt())
    }

    fun getBreeds(speciesId: Long?): List<Map<String, Any>> {
        val breeds = if (speciesId != null) {
            breedRepository.findAllByPetSpeciesId(speciesId.toInt())
        } else {
            breedRepository.findAll()
        }
        return breeds.map { breed ->
            mapOf(
                "id" to breed.id,
                "name" to breed.name,
                "description" to (breed.description ?: ""),
                "speciesId" to breed.petSpecies.id,
                "speciesName" to breed.petSpecies.name
            )
        }
    }

    @Transactional
    @CacheEvict(value = ["breeds"], allEntries = true)
    fun createBreed(request: CreateBreedRequest): Map<String, Any> {
        val name = request.name
        val speciesId = request.speciesId
        val description = request.description
        val species = speciesRepository.findById(speciesId).orElseThrow { NotFoundException("Species not found") }
        val breed = PetBreed(name = name, petSpecies = species, description = description)
        val saved = breedRepository.save(breed)
        return mapOf("id" to saved.id, "name" to saved.name, "speciesId" to species.id, "speciesName" to species.name)
    }

    @Transactional
    @CacheEvict(value = ["breeds"], allEntries = true)
    fun deleteBreed(id: Long) {
        breedRepository.deleteById(id.toInt())
    }

    fun getCategories(): List<Map<String, Any>> {
        return categoryRepository.findAll().map { category ->
            mapOf(
                "id" to category.id,
                "code" to category.code,
                "name" to category.name
            )
        }
    }

    @Transactional
    @CacheEvict(value = ["community:categories", "community:categories:entities"], allEntries = true)
    fun createCategory(request: CreateCategoryRequest): Map<String, Any> {
        val name = request.name
        val code = request.code ?: name.uppercase().replace(" ", "_")
        val category = com.goldpet.domain.community.entity.CommunityCategory(
            name = name,
            code = code
        )
        val saved = categoryRepository.save(category)
        return mapOf("id" to saved.id, "code" to saved.code, "name" to saved.name)
    }

    @Transactional
    @CacheEvict(value = ["community:categories", "community:categories:entities"], allEntries = true)
    fun deleteCategory(id: Long) {
        categoryRepository.deleteById(id)
    }

    fun getInterests(): List<Map<String, Any>> {
        return interestRepository.findAllByOrderByOrderIndexAsc().map {
            mapOf("id" to it.id, "name" to it.name, "orderIndex" to it.orderIndex)
        }
    }

    @Transactional
    @CacheEvict(value = ["user:interests"], allEntries = true)
    fun createInterest(request: CreateInterestRequest): Map<String, Any> {
        val name = request.name
        val orderIndex = request.orderIndex
        val interest = com.goldpet.domain.user.entity.Interest(name = name, orderIndex = orderIndex)
        val saved = interestRepository.save(interest)
        return mapOf("id" to saved.id, "name" to saved.name, "orderIndex" to saved.orderIndex)
    }

    @Transactional
    @CacheEvict(value = ["user:interests"], allEntries = true)
    fun deleteInterest(id: Long) {
        interestRepository.deleteById(id)
    }

    fun getHobbies(): List<Map<String, Any>> {
        return hobbyRepository.findAllByOrderByOrderIndexAsc().map {
            mapOf("id" to it.id, "name" to it.name, "orderIndex" to it.orderIndex)
        }
    }

    @Transactional
    @CacheEvict(value = ["user:hobbies"], allEntries = true)
    fun createHobby(request: CreateHobbyRequest): Map<String, Any> {
        val name = request.name
        val orderIndex = request.orderIndex
        val hobby = com.goldpet.domain.user.entity.Hobby(name = name, orderIndex = orderIndex)
        val saved = hobbyRepository.save(hobby)
        return mapOf("id" to saved.id, "name" to saved.name, "orderIndex" to saved.orderIndex)
    }

    @Transactional
    @CacheEvict(value = ["user:hobbies"], allEntries = true)
    fun deleteHobby(id: Long) {
        hobbyRepository.deleteById(id)
    }
}
