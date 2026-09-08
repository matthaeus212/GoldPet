package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.dto.PetAttributeRequest
import com.goldpet.domain.admin.dto.PetAttributeResponse
import com.goldpet.domain.admin.service.AttributeAdminService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.net.URI

@Tag(name = "어드민: 반려동물 속성 관리", description = "반려동물 속성(성격, 특성 등) CRUD")
@RestController
@RequestMapping("/api/v1/admin/pet-attributes")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminAttributeController(
    private val attributeService: AttributeAdminService
) {

    @Operation(summary = "전체 반려동물 속성 조회")
    @GetMapping
    fun getAllAttributes(): ResponseEntity<List<PetAttributeResponse>> {
        return ResponseEntity.ok(attributeService.getAllAttributes())
    }

    @Operation(summary = "반려동물 속성 생성")
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createAttribute(@RequestBody request: PetAttributeRequest): ResponseEntity<PetAttributeResponse> {
        val created = attributeService.createAttribute(request)
        return ResponseEntity.created(URI.create("/api/v1/admin/pet-attributes/${created.id}")).body(created)
    }

    @Operation(summary = "반려동물 속성 수정")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun updateAttribute(
        @PathVariable id: Long,
        @RequestBody request: PetAttributeRequest
    ): ResponseEntity<PetAttributeResponse> {
        return ResponseEntity.ok(attributeService.updateAttribute(id, request))
    }

    @Operation(summary = "반려동물 속성 삭제")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteAttribute(@PathVariable id: Long): ResponseEntity<Void> {
        attributeService.deleteAttribute(id)
        return ResponseEntity.noContent().build()
    }
}
