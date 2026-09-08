package com.goldpet.domain.admin.controller

import com.goldpet.common.net.TrustedClientIpResolver
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.service.AdminAuditService
import com.goldpet.domain.auth.service.DevLoginAccessPolicy
import com.goldpet.domain.auth.service.DevLoginIpAllowlistService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

/**
 * dev-login 허용 IP 관리.
 *
 * dev-login 은 비밀번호·SNS 인증 없이 토큰을 발급하므로, 이 목록을 바꾸는 것은 **인증 통제를
 * 바꾸는 것**이다. SUPER_ADMIN 만 허용하고 모든 변경을 감사 로그에 남긴다.
 */
@Tag(name = "Admin: dev-login 접근 통제", description = "dev-login 허용 IP 관리")
@RestController
@RequestMapping("/api/v1/admin/dev-login")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class AdminDevLoginController(
    private val ipAllowlistService: DevLoginIpAllowlistService,
    private val devLoginAccessPolicy: DevLoginAccessPolicy,
    private val trustedClientIpResolver: TrustedClientIpResolver,
    private val adminAuditService: AdminAuditService
) {
    @Operation(summary = "허용 IP 목록 조회 + 현재 상태")
    @GetMapping("/whitelist")
    fun list(
        request: HttpServletRequest
    ): ResponseEntity<DevLoginWhitelistView> {
        val myIp = trustedClientIpResolver.resolveOrNull(request)
        val entries = ipAllowlistService.findAll().map { DevLoginIpEntryResponse.from(it) }
        return ResponseEntity.ok(
            DevLoginWhitelistView(
                enabled = devLoginAccessPolicy.enabled(),
                allowedEmails = devLoginAccessPolicy.allowedEmails(),
                // 관리자가 자기 IP 를 눈으로 확인하고 등록할 수 있게 돌려준다(오타·추측 방지).
                requesterIp = myIp,
                entries = entries
            )
        )
    }

    @Operation(summary = "허용 IP 등록")
    @PostMapping("/whitelist")
    fun create(
        @Valid @RequestBody request: CreateDevLoginIpRequest,
        @AuthenticationPrincipal principal: AdminUserPrincipal,
        httpRequest: HttpServletRequest
    ): ResponseEntity<DevLoginIpEntryResponse> {
        val created = ipAllowlistService.create(
            ipPattern = request.ipPattern,
            label = request.label,
            expiresAt = request.expiresAt,
            adminId = principal.id
        )
        audit(principal.id, "DEV_LOGIN_IP_CREATE", created.id, httpRequest, "${created.ipPattern} (${created.label})")
        return ResponseEntity.ok(DevLoginIpEntryResponse.from(created))
    }

    @Operation(summary = "허용 IP 수정 (라벨/활성/만료)")
    @PutMapping("/whitelist/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: UpdateDevLoginIpRequest,
        @AuthenticationPrincipal principal: AdminUserPrincipal,
        httpRequest: HttpServletRequest
    ): ResponseEntity<DevLoginIpEntryResponse> {
        val updated = ipAllowlistService.update(
            id = id,
            label = request.label,
            enabled = request.enabled,
            expiresAt = request.expiresAt,
            clearExpiry = request.clearExpiry,
            requesterIp = trustedClientIpResolver.resolveOrNull(httpRequest),
            confirmSelfLockout = request.confirmSelfLockout
        )
        audit(principal.id, "DEV_LOGIN_IP_UPDATE", id, httpRequest, "${updated.ipPattern} enabled=${updated.enabled}")
        return ResponseEntity.ok(DevLoginIpEntryResponse.from(updated))
    }

    @Operation(summary = "허용 IP 삭제")
    @DeleteMapping("/whitelist/{id}")
    fun delete(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") confirmSelfLockout: Boolean,
        @AuthenticationPrincipal principal: AdminUserPrincipal,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        ipAllowlistService.delete(
            id = id,
            requesterIp = trustedClientIpResolver.resolveOrNull(httpRequest),
            confirmSelfLockout = confirmSelfLockout
        )
        audit(principal.id, "DEV_LOGIN_IP_DELETE", id, httpRequest, null)
        return ResponseEntity.noContent().build()
    }

    private fun audit(
        adminId: Long,
        action: String,
        targetId: Long?,
        request: HttpServletRequest,
        details: String?
    ) {
        adminAuditService.log(
            adminUserId = adminId,
            action = action,
            targetType = "DEV_LOGIN_IP_ALLOWLIST",
            targetId = targetId,
            ipAddress = trustedClientIpResolver.resolveOrNull(request),
            requestPath = request.requestURI,
            requestMethod = request.method,
            details = details
        )
    }
}

data class DevLoginWhitelistView(
    /** 마스터 킬스위치(SystemSetting `devlogin.enabled`). false 면 IP 와 무관하게 전면 차단. */
    val enabled: Boolean,
    val allowedEmails: List<String>,
    /** 이 요청을 보낸 관리자의 IP(X-Real-IP 기준). 등록 시 오타·추측을 막기 위해 돌려준다. */
    val requesterIp: String?,
    val entries: List<DevLoginIpEntryResponse>
)

data class DevLoginIpEntryResponse(
    val id: Long,
    val ipPattern: String,
    val label: String,
    val enabled: Boolean,
    val expiresAt: LocalDateTime?,
    val active: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(e: com.goldpet.domain.auth.entity.DevLoginIpAllow) = DevLoginIpEntryResponse(
            id = e.id,
            ipPattern = e.ipPattern,
            label = e.label,
            enabled = e.enabled,
            expiresAt = e.expiresAt,
            active = e.isActive(),
            createdAt = e.createdAt
        )
    }
}

data class CreateDevLoginIpRequest(
    @field:NotBlank(message = "IP 또는 CIDR 을 입력하세요")
    val ipPattern: String,
    @field:NotBlank(message = "라벨을 입력하세요")
    val label: String,
    /** null = 무기한. 임시 QA IP 는 만료를 걸어 방치되지 않게 한다. */
    val expiresAt: LocalDateTime? = null
)

data class UpdateDevLoginIpRequest(
    val label: String? = null,
    val enabled: Boolean? = null,
    val expiresAt: LocalDateTime? = null,
    val clearExpiry: Boolean = false,
    /** 이 변경으로 본인이 잠기는 것을 알고도 진행할 때만 true. */
    val confirmSelfLockout: Boolean = false
)
