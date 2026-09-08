package com.goldpet.domain.pet.controller

import com.goldpet.domain.pet.dto.CreatePetRequest
import com.goldpet.domain.pet.dto.PetResponse
import com.goldpet.domain.pet.dto.UpdatePetRequest
import com.goldpet.domain.pet.service.PetService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Pet", description = "반려동물 관리 API")
@RestController
@RequestMapping("/api/v1/pets")
class PetController(
    private val petService: PetService,
    private val userService: com.goldpet.domain.user.service.UserService
) {

    @Operation(summary = "반려동물 등록")
    @PostMapping
    fun createPet(
        @AuthenticationPrincipal principal: UserDetails?,
        @RequestBody @Valid request: CreatePetRequest
    ): ResponseEntity<PetResponse> {
        if (principal == null) return ResponseEntity.status(401).build()
        val userId = userService.getUserIdFromPrincipal(principal)
        val petResponse = petService.createPet(userId, request)
        return ResponseEntity.created(URI.create("/api/v1/pets/${petResponse.id}")).body(petResponse)
    }

    @Operation(summary = "반려동물 조회")
    @GetMapping("/{petId}")
    fun getPet(@PathVariable petId: Long): ResponseEntity<PetResponse> {
        val petResponse = petService.getPet(petId)
        return ResponseEntity.ok(petResponse)
    }

    @Operation(summary = "내 반려동물 목록 조회")
    @GetMapping("/my")
    fun getMyPets(@AuthenticationPrincipal principal: UserDetails?): ResponseEntity<List<PetResponse>> {
        if (principal == null) return ResponseEntity.status(401).build()
        val userId = userService.getUserIdFromPrincipal(principal)
        val petResponses = petService.getPetsByOwner(userId)
        return ResponseEntity.ok(petResponses)
    }

    @Operation(summary = "반려동물 정보 수정")
    @PutMapping("/{petId}")
    fun updatePet(
        @AuthenticationPrincipal principal: UserDetails?,
        @PathVariable petId: Long,
        @RequestBody @Valid request: UpdatePetRequest
    ): ResponseEntity<PetResponse> {
        if (principal == null) return ResponseEntity.status(401).build()
        val userId = userService.getUserIdFromPrincipal(principal)
        val petResponse = petService.updatePet(userId, petId, request)
        return ResponseEntity.ok(petResponse)
    }

    @Operation(summary = "반려동물 삭제")
    @DeleteMapping("/{petId}")
    fun deletePet(
        @AuthenticationPrincipal principal: UserDetails?,
        @PathVariable petId: Long
    ): ResponseEntity<Void> {
        if (principal == null) return ResponseEntity.status(401).build()
        val userId = userService.getUserIdFromPrincipal(principal)
        petService.deletePet(userId, petId)
        return ResponseEntity.noContent().build()
    }
}
