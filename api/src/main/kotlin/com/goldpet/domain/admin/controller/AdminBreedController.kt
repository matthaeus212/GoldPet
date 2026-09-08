package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.dto.CreateBreedRequest
import com.goldpet.domain.admin.dto.UpdateBreedRequest
import com.goldpet.domain.admin.service.BreedAdminService
import com.goldpet.domain.pet.dto.BreedResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "어드민: 품종 관리", description = "반려동물 품종 CRUD")
@RestController
@RequestMapping("/api/v1/admin/breeds")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminBreedController(
    private val breedAdminService: BreedAdminService
) {

    @Operation(summary = "전체 품종 목록 조회")
    @GetMapping
    fun getAllBreeds(
        @RequestParam(required = false) speciesId: Int?
    ): ResponseEntity<List<BreedResponse>> {
        val breeds = if (speciesId != null) {
            breedAdminService.getBreedsBySpecies(speciesId)
        } else {
            breedAdminService.getAllBreeds()
        }
        return ResponseEntity.ok(breeds)
    }

    @Operation(summary = "품종 단건 조회")
    @GetMapping("/{breedId}")
    fun getBreed(@PathVariable breedId: Int): ResponseEntity<BreedResponse> {
        val breed = breedAdminService.getBreed(breedId)
        return ResponseEntity.ok(breed)
    }

    @Operation(summary = "품종 생성")
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createBreed(@RequestBody request: CreateBreedRequest): ResponseEntity<BreedResponse> {
        val created = breedAdminService.createBreed(request)
        return ResponseEntity.created(URI.create("/api/v1/admin/breeds/${created.id}")).body(created)
    }

    @Operation(summary = "품종 수정")
    @PutMapping("/{breedId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun updateBreed(
        @PathVariable breedId: Int,
        @RequestBody request: UpdateBreedRequest
    ): ResponseEntity<BreedResponse> {
        val updated = breedAdminService.updateBreed(breedId, request)
        return ResponseEntity.ok(updated)
    }

    @Operation(summary = "품종 삭제")
    @DeleteMapping("/{breedId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteBreed(@PathVariable breedId: Int): ResponseEntity<Void> {
        breedAdminService.deleteBreed(breedId)
        return ResponseEntity.noContent().build()
    }
}
