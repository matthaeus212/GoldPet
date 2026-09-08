package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.dto.PetAttributeRequest
import com.goldpet.domain.admin.dto.PetAttributeResponse
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.pet.entity.PetAttribute
import com.goldpet.domain.pet.repository.PetAttributeRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AttributeAdminService(
    private val attributeRepository: PetAttributeRepository
) {

    @Cacheable(value = ["pet_attributes"], key = "'all'")
    fun getAllAttributes(): List<PetAttributeResponse> {
        return attributeRepository.findAllByOrderByDisplayOrderAsc()
            .map { PetAttributeResponse.from(it) }
    }

    @Transactional
    @CacheEvict(value = ["pet_attributes"], allEntries = true)
    fun createAttribute(request: PetAttributeRequest): PetAttributeResponse {
        val attribute = PetAttribute(
            category = request.category,
            code = request.code,
            name = request.name,
            inputType = request.inputType,
            displayOrder = request.displayOrder,
            options = request.options
        )
        val saved = attributeRepository.save(attribute)
        return PetAttributeResponse.from(saved)
    }

    @Transactional
    @CacheEvict(value = ["pet_attributes"], allEntries = true)
    fun updateAttribute(id: Long, request: PetAttributeRequest): PetAttributeResponse {
        val attribute = attributeRepository.findById(id)
            .orElseThrow { NotFoundException("Attribute not found") }
        
        attribute.update(
            name = request.name,
            inputType = request.inputType,
            displayOrder = request.displayOrder,
            options = request.options
        )
        
        // Code and Category are generally not updatable to avoid data inconsistency, or careful handling is needed.
        // For now, assuming code/category are fixed after creation or re-creation.
        
        return PetAttributeResponse.from(attributeRepository.save(attribute))
    }

    @Transactional
    @CacheEvict(value = ["pet_attributes"], allEntries = true)
    fun deleteAttribute(id: Long) {
        if (!attributeRepository.existsById(id)) {
            throw NotFoundException("Attribute not found")
        }
        attributeRepository.deleteById(id)
    }
}
