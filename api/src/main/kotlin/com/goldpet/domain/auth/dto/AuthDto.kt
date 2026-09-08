package com.goldpet.domain.auth.dto

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// Request DTO for traditional signup
data class SignupRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 4, max = 20)
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 100)
    val password: String,

    @field:NotBlank(message = "Nickname is required")
    val nickname: String,

    @field:NotBlank(message = "Name is required")
    val name: String,

    // Apple Guideline 5.1.1(v): demographic fields must be optional, not required.
    val birthDate: String?,

    val phoneNumber: String?,

    @field:Email(message = "Invalid email format")
    val email: String?,

    val gender: String?,
    val birthYear: Int?,
    val mainLocationText: String?
)

// Request DTO for login (username/password)
data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

// Response DTO after successful authentication
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserInfo,
    val linkSuggestion: LinkSuggestionInfo? = null
) {
    data class UserInfo(
        val id: Long,
        val username: String,
        val nickname: String,
        val email: String?,
        val phoneNumber: String? = null,
        // name은 표시/프로필 값일 뿐 완료 신호가 아니다(장기적으로 선택 항목 검토 대상).
        val name: String? = null,
        // 온보딩/추가가입 완료 단일 기준 = user.signupCompletedAt != null.
        // first-touch OAuth/링크제안 stub은 false.
        val signupCompleted: Boolean = false
    )
}

data class LinkSuggestionInfo(
    val maskedEmail: String,
    val existingProvider: String,
    val tempToken: String
)

data class ConfirmLinkRequest(
    @field:NotBlank(message = "Temp token is required")
    val tempToken: String
)

data class CheckUsernameRequest(
    @field:NotBlank(message = "Username is required")
    val username: String
)

data class CheckNicknameRequest(
    @field:NotBlank(message = "Nickname is required")
    val nickname: String
)

data class CheckEmailRequest(
    @field:NotBlank(message = "Email is required")
    @field:jakarta.validation.constraints.Email(message = "Invalid email format")
    val email: String
)

data class EmailAvailabilityResponse(
    val available: Boolean,
    val provider: String? = null
)

data class NativeLoginRequest(
    @field:NotBlank(message = "Access token is required")
    val accessToken: String
)

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)

// Account recovery DTOs
data class FindUsernameRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FindUsernameResponse(
    val username: String,
    val snsProvider: String? = null
)

data class VerifyAccountRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String
)

data class ResetPasswordRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 100)
    val password: String,

    val resetToken: String? = null
)
