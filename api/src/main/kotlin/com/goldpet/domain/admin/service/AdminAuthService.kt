package com.goldpet.domain.admin.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.common.exception.BadRequestException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * 로그인 결과. 2FA 미설정 시 [token] 을, 2FA 설정 시 [challengeId] 를 담는다(둘 중 하나).
 * EXT-CDX-005 — verify-2fa 는 이 challengeId 에만 바인딩된다(userId 직접 호출 불가).
 */
data class AdminLoginResult(
    val token: String?,
    val challengeId: String?,
    val adminUser: AdminUser
)

@Service
class AdminAuthService(
    private val adminUserRepository: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val adminTotpService: AdminTotpService
) {

    companion object {
        val ADMIN_PASSWORD_REGEX = Regex("^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#\$%^&*()_+\\-=\\[\\]{}|;:',.<>?/~`]).{8,}$")

        /** V69 — TOTP code 재사용 차단 window (Google Authenticator 30s step + 약간의 여유). */
        const val TOTP_REPLAY_WINDOW_SECONDS: Long = 30

        /** EXT-CDX-005 — 2FA challenge 유효시간(분). password 인증 후 이 창 안에서만 verify-2fa 가능. */
        const val CHALLENGE_TTL_MINUTES: Long = 5

        /** EXT-CDX-005 — TOTP 시도 lockout 임계값 및 window(분). */
        const val MAX_2FA_ATTEMPTS: Int = 5
        const val LOCKOUT_TTL_MINUTES: Long = 15
    }

    // EXT-CDX-005 — challengeId → userId. password 인증 성공 시 발급, verify-2fa 성공 시 단일 소비.
    private val challengeStore: Cache<String, Long> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(CHALLENGE_TTL_MINUTES))
        .maximumSize(10_000)
        .build()

    // EXT-CDX-005 — userId → 실패 누적. 임계값 초과 시 lockout window 동안 verify-2fa 거부.
    private val failedAttempts: Cache<Long, Int> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(LOCKOUT_TTL_MINUTES))
        .maximumSize(10_000)
        .build()

    @Transactional
    fun login(email: String, passwordRaw: String): AdminLoginResult {
        val emailHash = BlindIndexUtil.hash(email)
            ?: throw BadCredentialsException("Invalid Email or Password")
        val adminUser = adminUserRepository.findByEmailHash(emailHash)
            .orElseThrow { BadCredentialsException("Invalid Email or Password") }

        if (!passwordEncoder.matches(passwordRaw, adminUser.passwordHash)) {
            throw BadCredentialsException("Invalid Email or Password")
        }

        if (!adminUser.isActive) {
            throw BadCredentialsException("Account is disabled")
        }

        adminUser.lastLoginAt = LocalDateTime.now()

        // 2FA 설정 시: token 대신 서버측 단기 challengeId 발급(password 단계와 바인딩).
        if (!adminUser.otpSecret.isNullOrBlank()) {
            val challengeId = UUID.randomUUID().toString()
            challengeStore.put(challengeId, adminUser.id)
            return AdminLoginResult(token = null, challengeId = challengeId, adminUser = adminUser)
        }

        // 2FA 미설정: 정상 토큰 발급.
        val token = generateToken(adminUser)
        return AdminLoginResult(token = token, challengeId = null, adminUser = adminUser)
    }

    @Transactional
    fun changePassword(adminUserId: Long, currentPassword: String, newPassword: String) {
        val adminUser = adminUserRepository.findById(adminUserId)
            .orElseThrow { BadCredentialsException("User not found") }

        if (!passwordEncoder.matches(currentPassword, adminUser.passwordHash)) {
            throw BadCredentialsException("Invalid current password")
        }

        if (!ADMIN_PASSWORD_REGEX.matches(newPassword)) {
            throw BadRequestException("비밀번호는 8자 이상이며, 영문, 숫자, 특수문자를 포함해야 합니다.")
        }

        adminUser.passwordHash = passwordEncoder.encode(newPassword)
        adminUser.mustChangePassword = false
    }

    /**
     * EXT-CDX-005 — password 단계에서 발급된 [challengeId] 에만 바인딩된 2FA 검증.
     * challenge 없이 userId+code 로 직접 토큰을 받을 수 없다(공개 endpoint 우회 차단).
     * 시도 lockout([MAX_2FA_ATTEMPTS]) + V69 replay window 유지.
     */
    @Transactional
    fun verify2fa(challengeId: String, code: Int): Pair<String, AdminUser> {
        val userId = challengeStore.getIfPresent(challengeId)
            ?: throw BadCredentialsException("2FA challenge not found or expired")

        val attempts = failedAttempts.getIfPresent(userId) ?: 0
        if (attempts >= MAX_2FA_ATTEMPTS) {
            throw BadCredentialsException("Too many 2FA attempts — try again later")
        }

        val adminUser = adminUserRepository.findById(userId)
            .orElseThrow { BadCredentialsException("User not found") }

        if (adminUser.otpSecret.isNullOrBlank()) {
            throw BadCredentialsException("2FA not set up")
        }

        if (!adminTotpService.validateCode(adminUser.otpSecret!!, code)) {
            failedAttempts.put(userId, attempts + 1)
            throw BadCredentialsException("Invalid 2FA Code")
        }

        // V69 — replay 차단. 동일 30s window 코드 재사용 거부 (탈취된 OTP screenshot 재사용 방지).
        val now = LocalDateTime.now()
        val lastUsed = adminUser.lastOtpUsedAt
        if (lastUsed != null && Duration.between(lastUsed, now).seconds < TOTP_REPLAY_WINDOW_SECONDS) {
            throw BadCredentialsException("2FA Code already used recently — wait for next code")
        }
        adminUser.lastOtpUsedAt = now

        // 성공: challenge 단일 소비 + lockout 카운터 초기화.
        challengeStore.invalidate(challengeId)
        failedAttempts.invalidate(userId)

        return Pair(generateToken(adminUser), adminUser)
    }

    @Transactional
    fun setup2fa(userId: Long): Pair<String, String> {
        val adminUser = adminUserRepository.findById(userId)
            .orElseThrow { RuntimeException("User not found") }
        
        val secret = adminTotpService.generateSecret()
        val qrUrl = adminTotpService.getQrCodeUrl(secret, adminUser.email)
        
        return Pair(secret, qrUrl)
    }

    @Transactional
    fun confirm2fa(userId: Long, secret: String, code: Int) {
        val adminUser = adminUserRepository.findById(userId)
            .orElseThrow { RuntimeException("User not found") }

        if (!adminTotpService.validateCode(secret, code)) {
            throw BadRequestException("Invalid Code")
        }

        adminUser.otpSecret = secret
        // Hibernate dirty checking saves it
    }
    
    @Transactional
    fun cancel2fa(userId: Long) {
         val adminUser = adminUserRepository.findById(userId)
            .orElseThrow { RuntimeException("User not found") }
         adminUser.otpSecret = null
    }

    private fun generateToken(adminUser: AdminUser): String {
        val principal = AdminUserPrincipal(adminUser)
        val authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            principal, null, principal.authorities
        )
        return jwtTokenProvider.generateToken(authentication)
    }
}
