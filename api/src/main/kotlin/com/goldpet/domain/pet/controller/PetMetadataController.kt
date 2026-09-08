package com.goldpet.domain.pet.controller

import com.goldpet.domain.pet.dto.BreedResponse
import com.goldpet.domain.pet.dto.SpeciesResponse
import com.goldpet.domain.pet.service.PetService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Pet Metadata", description = "반려동물 종/품종 메타데이터 API")
@RestController
@RequestMapping("/api/v1/pets")
class PetMetadataController(
    private val petService: PetService
) {

    @Operation(summary = "동물 종 목록 조회")
    @GetMapping("/species")
    fun getAllSpecies(): ResponseEntity<List<SpeciesResponse>> {
        return ResponseEntity.ok(petService.getAllSpecies())
    }

    @Operation(summary = "품종 목록 조회")
    @GetMapping("/breeds")
    fun getBreeds(@RequestParam(required = true) speciesId: Int): ResponseEntity<List<BreedResponse>> {
        return ResponseEntity.ok(petService.getBreedsBySpecies(speciesId))
    }

    @Operation(summary = "반려동물 특성 목록 조회")
    @GetMapping("/attributes")
    fun getPetAttributes(): ResponseEntity<List<com.goldpet.domain.admin.dto.PetAttributeResponse>> {
        return ResponseEntity.ok(petService.getPetAttributes())
    }
}
