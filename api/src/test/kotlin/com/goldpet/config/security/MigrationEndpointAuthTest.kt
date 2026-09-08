package com.goldpet.config.security

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.admin.service.PiiKeyRotationService
import com.goldpet.domain.admin.service.PiiKeyRotationService.RotationResult
import com.goldpet.domain.admin.service.PiiKeyRotationService.VerificationResult
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Three-sided auth test for the rotation endpoints (C1 fix verification).
 *
 * Sides:
 *  1. Anonymous          → 401
 *  2. Regular user JWT   → 403 (authenticated but no admin role)
 *  3. OPERATOR JWT       → 403 (admin, but SUPER_ADMIN-only endpoint)
 *  4. SUPER_ADMIN JWT    → 200 (C1 fix proves authority string resolves correctly)
 *
 * PiiKeyRotationService is mocked so these tests are auth-only with no DB rotation side-effects.
 */
@AutoConfigureMockMvc
@Tag("integration")
class MigrationEndpointAuthTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @MockBean private lateinit var piiKeyRotationService: PiiKeyRotationService

    private val ts = System.currentTimeMillis()
    private lateinit var regularUser: User
    private lateinit var superAdminUser: AdminUser
    private lateinit var operatorAdminUser: AdminUser

    private val successResult = RotationResult(
        rotated = 0,
        skippedAlreadyNew = 0,
        failed = 0,
        mismatches = emptyList(),
        verificationPass = VerificationResult(expected = 0, matched = 0, mismatches = emptyList()),
        dryRun = false
    )

    @BeforeEach
    fun setUp() {
        regularUser = userRepository.save(User(
            id = 0,
            email = "regular_meat_$ts@goldpet.com",
            oauthProvider = "LOCAL",
            oauthId = "regular_meat_$ts",
            username = "regular_meat_$ts",
            password = passwordEncoder.encode("pass"),
            nickname = "regular_meat_$ts",
            name = "RegularUser",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        ))

        superAdminUser = adminUserRepository.save(AdminUser(
            email = "super_meat_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "SuperAdmin",
            role = AdminUserRole.SUPER_ADMIN,
            otpSecret = null,
            isActive = true
        ))

        operatorAdminUser = adminUserRepository.save(AdminUser(
            email = "operator_meat_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Operator",
            role = AdminUserRole.OPERATOR,
            otpSecret = null,
            isActive = true
        ))

        whenever(piiKeyRotationService.rotate(any(), any())).thenReturn(successResult)
    }

    @AfterEach
    fun tearDown() {
        runCatching { userRepository.delete(regularUser) }
        runCatching { adminUserRepository.delete(superAdminUser) }
        runCatching { adminUserRepository.delete(operatorAdminUser) }
    }

    // ── rotate endpoint ────────────────────────────────────────────────────────

    @Test
    fun `rotate returns 401 for anonymous request`() {
        mockMvc.perform(post("/api/v1/admin/migration/rotate-encryption-key"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `rotate returns 403 for regular user JWT`() {
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key")
                .header("Authorization", "Bearer ${userJwt()}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `rotate returns 403 for OPERATOR JWT`() {
        // Rotation is SUPER_ADMIN-only — OPERATOR must be rejected
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key")
                .header("Authorization", "Bearer ${operatorJwt()}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `rotate returns 200 for SUPER_ADMIN JWT — proves C1 authority fix wired correctly`() {
        // C1 fix: AdminUserPrincipal emits ROLE_SUPER_ADMIN (not the literal 40-char template).
        // If C1 were still broken, this would return 403 even for SUPER_ADMIN.
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key")
                .header("Authorization", "Bearer ${superAdminJwt()}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rotated").exists())
            .andExpect(jsonPath("$.failed").value(0))
    }

    // ── dry-run endpoint ───────────────────────────────────────────────────────

    @Test
    fun `dry-run returns 401 for anonymous request`() {
        mockMvc.perform(post("/api/v1/admin/migration/rotate-encryption-key/dry-run"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `dry-run returns 403 for regular user JWT`() {
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key/dry-run")
                .header("Authorization", "Bearer ${userJwt()}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `dry-run returns 403 for OPERATOR JWT`() {
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key/dry-run")
                .header("Authorization", "Bearer ${operatorJwt()}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `dry-run returns 200 for SUPER_ADMIN JWT`() {
        whenever(piiKeyRotationService.rotate(any(), any()))
            .thenReturn(successResult.copy(dryRun = true))
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key/dry-run")
                .header("Authorization", "Bearer ${superAdminJwt()}")
        ).andExpect(status().isOk)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun userJwt(): String {
        val principal = UserPrincipal.create(regularUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }

    private fun superAdminJwt(): String {
        val principal = AdminUserPrincipal(superAdminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }

    private fun operatorJwt(): String {
        val principal = AdminUserPrincipal(operatorAdminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }
}
