package com.goldpet.config.security

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * C2 fix verification: @EnableMethodSecurity activates the 4 @PreAuthorize annotations on admin
 * controllers. Without @EnableMethodSecurity those annotations are silently inert and any
 * authenticated user would reach the endpoints. This test proves enforcement is active.
 *
 * Endpoints under test (class-level @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")):
 *   GET /api/v1/admin/dashboard/stats     (AdminDashboardController)
 *   GET /api/v1/admin/users               (AdminUserManagementController)
 *   GET /api/v1/admin/community/posts     (AdminCommunityController)
 *   GET /api/v1/admin/courses             (AdminCourseController)
 *
 * F5 (2026-05-04): VIEWER role added to class-level guard (read-only matrix).
 */
@AutoConfigureMockMvc
@Tag("integration")
class AdminMethodSecurityActivationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val ts = System.currentTimeMillis()
    private lateinit var regularUser: User
    private lateinit var superAdminUser: AdminUser
    private lateinit var operatorAdminUser: AdminUser

    @BeforeEach
    fun setUp() {
        regularUser = userRepository.save(User(
            id = 0,
            email = "regular_ams_$ts@goldpet.com",
            oauthProvider = "LOCAL",
            oauthId = "regular_ams_$ts",
            username = "regular_ams_$ts",
            password = passwordEncoder.encode("pass"),
            nickname = "regular_ams_$ts",
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
            email = "super_ams_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "SuperAdmin",
            role = AdminUserRole.SUPER_ADMIN,
            otpSecret = null,
            isActive = true
        ))

        operatorAdminUser = adminUserRepository.save(AdminUser(
            email = "operator_ams_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Operator",
            role = AdminUserRole.OPERATOR,
            otpSecret = null,
            isActive = true
        ))
    }

    @AfterEach
    fun tearDown() {
        runCatching { userRepository.delete(regularUser) }
        runCatching { adminUserRepository.delete(superAdminUser) }
        runCatching { adminUserRepository.delete(operatorAdminUser) }
    }

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @Test
    fun `dashboard stats returns 403 for regular user`() {
        mockMvc.perform(
            get("/api/v1/admin/dashboard/stats")
                .header("Authorization", "Bearer ${userJwt()}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `dashboard stats returns 200 for SUPER_ADMIN`() {
        mockMvc.perform(
            get("/api/v1/admin/dashboard/stats")
                .header("Authorization", "Bearer ${superAdminJwt()}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `dashboard stats returns 200 for OPERATOR`() {
        // @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')") — OPERATOR is allowed (Phase 0.A fix + F5 VIEWER add)
        mockMvc.perform(
            get("/api/v1/admin/dashboard/stats")
                .header("Authorization", "Bearer ${operatorJwt()}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `dashboard stats returns 401 without any JWT`() {
        mockMvc.perform(get("/api/v1/admin/dashboard/stats"))
            .andExpect(status().isUnauthorized)
    }

    // ── User management ────────────────────────────────────────────────────────

    @Test
    fun `admin users endpoint returns 403 for regular user`() {
        mockMvc.perform(
            get("/api/v1/admin/users")
                .header("Authorization", "Bearer ${userJwt()}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin users endpoint allows SUPER_ADMIN`() {
        mockMvc.perform(
            get("/api/v1/admin/users")
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
