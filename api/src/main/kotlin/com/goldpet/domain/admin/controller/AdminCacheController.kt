package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.dto.CacheClearLogResponse
import com.goldpet.domain.admin.dto.CacheClearRequest
import com.goldpet.domain.admin.dto.CacheClearResult
import com.goldpet.domain.admin.dto.CacheInfoResponse
import com.goldpet.domain.admin.service.AdminCacheService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin Cache Management", description = "관리자 캐시 관리 API")
@RestController
@RequestMapping("/api/v1/admin/cache")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminCacheController(
    private val adminCacheService: AdminCacheService
) {
    @Operation(summary = "캐시 상태 조회")
    @GetMapping("/status")
    fun getCacheStatus(): ResponseEntity<List<CacheInfoResponse>> {
        return ResponseEntity.ok(adminCacheService.getCacheStatus())
    }

    @Operation(summary = "특정 캐시 초기화")
    @PostMapping("/clear")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun clearCache(@RequestBody request: CacheClearRequest): ResponseEntity<CacheClearResult> {
        return ResponseEntity.ok(adminCacheService.clearCache(request.cacheName))
    }

    @Operation(summary = "전체 캐시 초기화")
    @PostMapping("/clear-all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun clearAllCaches(): ResponseEntity<CacheClearResult> {
        return ResponseEntity.ok(adminCacheService.clearAllCaches())
    }

    @Operation(summary = "캐시 초기화 로그 조회")
    @GetMapping("/logs")
    fun getClearLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<List<CacheClearLogResponse>> {
        return ResponseEntity.ok(adminCacheService.getClearLogs(page, size))
    }
}
