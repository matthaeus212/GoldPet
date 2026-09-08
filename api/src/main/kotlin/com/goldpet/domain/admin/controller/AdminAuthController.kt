package com.goldpet.domain.admin.controller

import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.service.AdminAuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

data class AdminLoginRequest(
    val email: String,
    val password: String
)

data class AdminLoginResponse(
    val requiresTwoFactor: Boolean = false,
    val token: String? = null,
    /** EXT-CDX-005 — 2FA 필요 시 서버측 challenge ID. verify-2fa 에 그대로 전달. */
    val twoFactorChallengeId: String? = null,
    val user: AdminUserDto? = null,
    val mustChangePassword: Boolean = false
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class Verify2faRequest(
    /** EXT-CDX-005 — login 이 발급한 challengeId (userId 직접 지정 불가). */
    val challengeId: String,
    val code: Int
)

data class Confirm2faRequest(
    val secret: String,
    val code: Int
)

data class Setup2faResponse(
    val secret: String,
    val qrUrl: String
)

data class AdminUserDto(
    val id: Long,
    val email: String,
    val name: String,
    val role: AdminUserRole,
    val isTwoFactorEnabled: Boolean
)

@Tag(name = "Admin Auth", description = "관리자 인증 API")
@RestController
@RequestMapping("/api/v1/admin/auth")
class AdminAuthController(
    private val adminAuthService: AdminAuthService
) {

    @Operation(summary = "관리자 로그인", description = "이메일/비밀번호로 로그인합니다. 2FA 설정된 경우 token은 null이고 requiresTwoFactor=true 입니다.")
    @PostMapping("/login")
    fun login(@RequestBody request: AdminLoginRequest): ResponseEntity<AdminLoginResponse> {
        val result = adminAuthService.login(request.email, request.password)
        val token = result.token
        val adminUser = result.adminUser

        val requiresTwoFactor = token == null && !adminUser.otpSecret.isNullOrBlank()

        return ResponseEntity.ok(AdminLoginResponse(
            requiresTwoFactor = requiresTwoFactor,
            token = token, // Will be null if 2FA is required
            twoFactorChallengeId = result.challengeId, // EXT-CDX-005: 2FA 필요 시에만 non-null
            user = AdminUserDto(
                id = adminUser.id,
                email = adminUser.email,
                name = adminUser.name,
                role = adminUser.role,
                isTwoFactorEnabled = !adminUser.otpSecret.isNullOrBlank()
            ),
            mustChangePassword = adminUser.mustChangePassword
        ))
    }

    @Operation(summary = "2FA 인증 확인", description = "로그인 후 2FA 코드를 검증하여 토큰을 발급받습니다.")
    @PostMapping("/verify-2fa")
    fun verify2fa(@RequestBody request: Verify2faRequest): ResponseEntity<AdminLoginResponse> {
        val (token, adminUser) = adminAuthService.verify2fa(request.challengeId, request.code)

        return ResponseEntity.ok(AdminLoginResponse(
            requiresTwoFactor = false,
            token = token,
            user = AdminUserDto(
                id = adminUser.id,
                email = adminUser.email,
                name = adminUser.name,
                role = adminUser.role,
                isTwoFactorEnabled = true
            ),
            mustChangePassword = adminUser.mustChangePassword
        ))
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 검증하고 새 비밀번호로 변경합니다. (로그인 필요)")
    @PostMapping("/change-password")
    fun changePassword(
        @AuthenticationPrincipal adminUserPrincipal: AdminUserPrincipal,
        @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<Void> {
        adminAuthService.changePassword(
            adminUserPrincipal.adminUser.id,
            request.currentPassword,
            request.newPassword
        )
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "2FA 설정 시작", description = "2FA 설정을 위한 시크릿 키와 QR 코드를 생성합니다. (로그인 필요)")
    @PostMapping("/2fa/setup")
    fun setup2fa(@AuthenticationPrincipal adminUserPrincipal: AdminUserPrincipal): ResponseEntity<Setup2faResponse> {
        val (secret, qrUrl) = adminAuthService.setup2fa(adminUserPrincipal.adminUser.id)
        return ResponseEntity.ok(Setup2faResponse(secret, qrUrl))
    }

    @Operation(summary = "2FA 설정 완료", description = "앱에 등록된 코드를 입력하여 2FA 설정을 최종 확정합니다. (로그인 필요)")
    @PostMapping("/2fa/confirm")
    fun confirm2fa(
        @AuthenticationPrincipal adminUserPrincipal: AdminUserPrincipal,
        @RequestBody request: Confirm2faRequest
    ): ResponseEntity<Void> {
        adminAuthService.confirm2fa(adminUserPrincipal.adminUser.id, request.secret, request.code)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "2FA 해제", description = "2FA 설정을 해제합니다. (로그인 필요)")
    @DeleteMapping("/2fa")
    fun remove2fa(@AuthenticationPrincipal adminUserPrincipal: AdminUserPrincipal): ResponseEntity<Void> {
        adminAuthService.cancel2fa(adminUserPrincipal.adminUser.id)
        return ResponseEntity.ok().build()
    }
}
