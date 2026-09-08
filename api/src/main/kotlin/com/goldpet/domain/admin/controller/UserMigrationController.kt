package com.goldpet.domain.admin.controller

import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.service.EmailHashMigrationService
import com.goldpet.domain.admin.service.PiiKeyRotationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "어드민: 사용자 마이그레이션", description = "PII 암호화 키 교체 및 이메일 해시 마이그레이션")
@RestController
@RequestMapping("/api/v1/admin/migration")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class UserMigrationController(
    private val emailHashMigrationService: EmailHashMigrationService,
    private val piiKeyRotationService: PiiKeyRotationService
) {
    private val log = LoggerFactory.getLogger(UserMigrationController::class.java)

    /**
     * Re-compute emailHash for ALL users from their decrypted email.
     * Fixes double-hashing corruption caused by the old BlindIndexConverter.
     */
    @Operation(summary = "이메일 해시 전체 재계산")
    @PostMapping("/email-hash/rehash")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun rehashAllEmailHashes(): ResponseEntity<Map<String, Any>> {
        // ARCH-005: 배치 로직은 EmailHashMigrationService 소관. 여기서는 응답 매핑만 한다.
        val r = emailHashMigrationService.rehashAllEmailHashes()
        return ResponseEntity.ok(mapOf(
            "totalUsers" to r.totalUsers,
            "fixed" to r.fixed,
            "skipped" to r.skipped,
            "duplicates" to r.duplicates
        ))
    }

    @Operation(summary = "이메일 해시 마이그레이션")
    @PostMapping("/email-hash")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun migrateEmailHash(): ResponseEntity<Map<String, Any>> {
        // ARCH-005: 배치 로직은 EmailHashMigrationService 소관. 여기서는 응답 매핑만 한다.
        val r = emailHashMigrationService.migrateEmailHash()
        return ResponseEntity.ok(mapOf(
            "totalUsersScanned" to r.totalUsersScanned,
            "updatedCount" to r.updatedCount,
            "duplicateGroupsFound" to r.duplicateGroupsFound,
            "mergedUsersCount" to r.mergedUsersCount
        ))
    }

    @Operation(summary = "PII 암호화 키 교체")
    // NOTE: No @Transactional here — PiiKeyRotationService.rotate() uses Propagation.NEVER.
    @PostMapping("/rotate-encryption-key")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun rotateEncryptionKey(
        @AuthenticationPrincipal principal: AdminUserPrincipal
    ): ResponseEntity<Any> = runRotation(dryRun = false, principalId = principal.id)

    @Operation(summary = "PII 암호화 키 교체 드라이런")
    // NOTE: No @Transactional here — PiiKeyRotationService.rotate() uses Propagation.NEVER.
    @PostMapping("/rotate-encryption-key/dry-run")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun rotateEncryptionKeyDryRun(
        @AuthenticationPrincipal principal: AdminUserPrincipal
    ): ResponseEntity<Any> = runRotation(dryRun = true, principalId = principal.id)

    private fun runRotation(dryRun: Boolean, principalId: Long): ResponseEntity<Any> {
        return try {
            val result = piiKeyRotationService.rotate(dryRun = dryRun, principalId = principalId)
            val hasFailure = result.failed > 0 ||
                result.mismatches.isNotEmpty() ||
                result.verificationPass.mismatches.isNotEmpty()
            if (hasFailure) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result)
            } else {
                ResponseEntity.ok(result)
            }
        } catch (e: PiiKeyRotationService.ConcurrentRotationException) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("error" to (e.message ?: "advisory lock busy")))
        }
    }
}
