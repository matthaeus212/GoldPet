package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.dto.*
import com.goldpet.domain.admin.service.AdminDataService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin Data", description = "관리자 코드/마스터 데이터 관리 API")
@RestController
@RequestMapping("/api/v1/admin/data")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminDataController(
    private val adminDataService: AdminDataService
) {
    // Pet Species
    @Operation(summary = "펫 종 목록")
    @GetMapping("/species")
    fun getSpecies(): ResponseEntity<List<Map<String, Any>>> {
        return ResponseEntity.ok(adminDataService.getSpecies())
    }

    @Operation(summary = "펫 종 추가")
    @PostMapping("/species")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createSpecies(@Valid @RequestBody request: CreateSpeciesRequest): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(adminDataService.createSpecies(request))
    }

    @Operation(summary = "펫 종 삭제")
    @DeleteMapping("/species/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteSpecies(@PathVariable id: Long): ResponseEntity<Void> {
        adminDataService.deleteSpecies(id)
        return ResponseEntity.noContent().build()
    }

    // Pet Breeds
    @Operation(summary = "펫 품종 목록")
    @GetMapping("/breeds")
    fun getBreeds(@RequestParam(required = false) speciesId: Long?): ResponseEntity<List<Map<String, Any>>> {
        return ResponseEntity.ok(adminDataService.getBreeds(speciesId))
    }

    @Operation(summary = "펫 품종 추가")
    @PostMapping("/breeds")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createBreed(@Valid @RequestBody request: CreateBreedRequest): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(adminDataService.createBreed(request))
    }

    @Operation(summary = "펫 품종 삭제")
    @DeleteMapping("/breeds/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteBreed(@PathVariable id: Long): ResponseEntity<Void> {
        adminDataService.deleteBreed(id)
        return ResponseEntity.noContent().build()
    }

    // Community Categories
    @Operation(summary = "커뮤니티 카테고리 목록")
    @GetMapping("/categories")
    fun getCategories(): ResponseEntity<List<Map<String, Any>>> {
        return ResponseEntity.ok(adminDataService.getCategories())
    }

    @Operation(summary = "커뮤니티 카테고리 추가")
    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createCategory(@Valid @RequestBody request: CreateCategoryRequest): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(adminDataService.createCategory(request))
    }

    @Operation(summary = "커뮤니티 카테고리 삭제")
    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteCategory(@PathVariable id: Long): ResponseEntity<Void> {
        adminDataService.deleteCategory(id)
        return ResponseEntity.noContent().build()
    }

    // Interests
    @Operation(summary = "관심사 목록")
    @GetMapping("/interests")
    fun getInterests(): ResponseEntity<List<Map<String, Any>>> {
        return ResponseEntity.ok(adminDataService.getInterests())
    }

    @Operation(summary = "관심사 추가")
    @PostMapping("/interests")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createInterest(@Valid @RequestBody request: CreateInterestRequest): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(adminDataService.createInterest(request))
    }

    @Operation(summary = "관심사 삭제")
    @DeleteMapping("/interests/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteInterest(@PathVariable id: Long): ResponseEntity<Void> {
        adminDataService.deleteInterest(id)
        return ResponseEntity.noContent().build()
    }

    // Hobbies
    @Operation(summary = "취미 목록")
    @GetMapping("/hobbies")
    fun getHobbies(): ResponseEntity<List<Map<String, Any>>> {
        return ResponseEntity.ok(adminDataService.getHobbies())
    }

    @Operation(summary = "취미 추가")
    @PostMapping("/hobbies")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createHobby(@Valid @RequestBody request: CreateHobbyRequest): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(adminDataService.createHobby(request))
    }

    @Operation(summary = "취미 삭제")
    @DeleteMapping("/hobbies/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteHobby(@PathVariable id: Long): ResponseEntity<Void> {
        adminDataService.deleteHobby(id)
        return ResponseEntity.noContent().build()
    }
}
