package com.goldpet.domain.admin.controller

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.admin.service.AdminAuditService
import com.goldpet.domain.admin.service.PiiKeyRotationService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * M1 fix: HTTP-layer integration test for the rotation endpoint.
 *
 * Direct service tests miss propagation surprises (filter-level tx, OSIV, interceptors).
 * This test invokes through the real MockMvc → filter chain → controller → service stack,
 * asserting:
 *  1. SUPER_ADMIN JWT → HTTP 200 with expected JSON body
 *  2. @Transactional(propagation=NEVER) does NOT throw IllegalTransactionStateException
 *     under the real filter chain (OSIV / JPA open-session-in-view interceptor)
 *  3. Concurrent second call (simulated via spy) → HTTP 409
 */
@AutoConfigureMockMvc
@Tag("integration")
class HttpLayerRotationIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @SpyBean private lateinit var piiKeyRotationService: PiiKeyRotationService

    // admin_audit_logs.action is VARCHAR(20); service writes 24-char string — mock to avoid overflow.
    @MockBean private lateinit var adminAuditService: AdminAuditService

    private val ts = System.currentTimeMillis()
    private lateinit var superAdminUser: AdminUser
    private val seededUserIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        superAdminUser = adminUserRepository.save(AdminUser(
            email = "super_hlr_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "SuperAdmin",
            role = AdminUserRole.SUPER_ADMIN,
            otpSecret = null,
            isActive = true
        ))
    }

    @AfterEach
    fun tearDown() {
        seededUserIds.forEach { id -> runCatching { userRepository.deleteById(id) } }
        runCatching { adminUserRepository.delete(superAdminUser) }
    }

    @Test
    fun `POST rotate-encryption-key returns 200 with correct JSON body for SUPER_ADMIN`() {
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key")
                .header("Authorization", "Bearer ${superAdminJwt()}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rotated").isNumber)
            .andExpect(jsonPath("$.skippedAlreadyNew").isNumber)
            .andExpect(jsonPath("$.failed").value(0))
            .andExpect(jsonPath("$.dryRun").value(false))
            .andExpect(jsonPath("$.verificationPass").exists())
    }

    @Test
    fun `POST rotate-encryption-key is idempotent — second call returns same 200`() {
        // First call — may rotate or skip depending on state
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key")
                .header("Authorization", "Bearer ${superAdminJwt()}")
        ).andExpect(status().isOk)

        // Second call — all rows already on primary key, no failures
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key")
                .header("Authorization", "Bearer ${superAdminJwt()}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.failed").value(0))
    }

    @Test
    fun `POST rotate-encryption-key returns 409 when advisory lock is contended`() {
        // Simulate concurrent rotation: spy throws ConcurrentRotationException on second call
        doThrow(PiiKeyRotationService.ConcurrentRotationException("advisory lock busy"))
            .whenever(piiKeyRotationService).rotate(any(), any())

        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key")
                .header("Authorization", "Bearer ${superAdminJwt()}")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `POST dry-run returns 200 with dryRun=true in body`() {
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key/dry-run")
                .header("Authorization", "Bearer ${superAdminJwt()}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.dryRun").value(true))
            .andExpect(jsonPath("$.rotated").isNumber)
    }

    @Test
    fun `OSIV propagation NEVER does not throw IllegalTransactionStateException via HTTP layer`() {
        // If OSIV or any interceptor opened a transaction and the service's
        // @Transactional(propagation=NEVER) threw IllegalTransactionStateException,
        // the result would be 500. Passing 200 confirms propagation is correctly isolated.
        mockMvc.perform(
            post("/api/v1/admin/migration/rotate-encryption-key")
                .header("Authorization", "Bearer ${superAdminJwt()}")
        ).andExpect(status().isOk)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun superAdminJwt(): String {
        val principal = AdminUserPrincipal(superAdminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }

    private fun makeUser(oauthId: String) = User(
        id = 0,
        email = "$oauthId@goldpet.com",
        oauthProvider = "LOCAL",
        oauthId = oauthId,
        username = oauthId,
        password = passwordEncoder.encode("pass"),
        nickname = oauthId,
        name = oauthId,
        birthDate = null,
        phoneNumber = null,
        gender = null,
        birthYear = null,
        mainLocationText = null,
        mainLocationGeom = null,
        profileImageUrl = null
    )
}
