package com.goldpet.domain.admin

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * JwtAuthenticationFilter isActive=false 즉시 무효화 검증.
 *
 * Spring Security UsernamePasswordAuthenticationToken은 UserDetails.isEnabled()를 자동 검증하지 않는다.
 * 필터에서 adminUserRepository.findById()로 최신 상태를 재조회해 is_active=false이면
 * filterChain.doFilter() 없이 즉시 401 + {"error":"ACCOUNT_DEACTIVATED"} 를 반환한다.
 */
@AutoConfigureMockMvc
@Tag("integration")
class JwtAuthFilterIsActiveTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val ts = System.currentTimeMillis()
    private lateinit var admin: AdminUser

    @BeforeEach
    fun setUp() {
        admin = adminUserRepository.save(AdminUser(
            email = "isactive_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "IsActive Test",
            role = AdminUserRole.OPERATOR,
            isActive = true
        ))
    }

    @AfterEach
    fun tearDown() {
        runCatching { adminUserRepository.deleteById(admin.id) }
    }

    @Test
    fun `valid JWT for deactivated admin returns 401 ACCOUNT_DEACTIVATED`() {
        val jwt = jwtFor(admin)

        // Deactivate directly via JDBC to bypass any JPA session cache
        jdbcTemplate.update("UPDATE admin_users SET is_active = false WHERE id = ?", admin.id)

        mockMvc.perform(
            get("/api/v1/admin/lbs/places")
                .header("Authorization", "Bearer $jwt")
        ).andExpect(status().isUnauthorized)
         .andExpect(jsonPath("$.error").value("ACCOUNT_DEACTIVATED"))
    }

    @Test
    fun `valid JWT for active admin passes filter and reaches endpoint - 200`() {
        mockMvc.perform(
            get("/api/v1/admin/lbs/places")
                .header("Authorization", "Bearer ${jwtFor(admin)}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `valid JWT for mustChangePassword admin is blocked on non-allowlisted endpoint with 403`() {
        val mcpAdmin = adminUserRepository.save(AdminUser(
            email = "mustchange_filter_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "MustChange Filter Test",
            role = AdminUserRole.OPERATOR,
            isActive = true,
            mustChangePassword = true
        ))
        try {
            mockMvc.perform(
                get("/api/v1/admin/lbs/places")
                    .header("Authorization", "Bearer ${jwtFor(mcpAdmin)}")
            ).andExpect(status().isForbidden)
             .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"))
        } finally {
            runCatching { adminUserRepository.deleteById(mcpAdmin.id) }
        }
    }

    @Test
    fun `valid JWT for mustChangePassword admin can access change-password allowlisted endpoint`() {
        val mcpAdmin = adminUserRepository.save(AdminUser(
            email = "mustchange_filter2_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("TempPass1!"),
            name = "MustChange Filter Test 2",
            role = AdminUserRole.OPERATOR,
            isActive = true,
            mustChangePassword = true
        ))
        try {
            // Filter must not block the change-password endpoint - expect the endpoint to handle the request (204)
            mockMvc.perform(
                post("/api/v1/admin/auth/change-password")
                    .header("Authorization", "Bearer ${jwtFor(mcpAdmin)}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"currentPassword":"TempPass1!","newPassword":"NewPass456@"}""")
            ).andExpect(status().isNoContent)
        } finally {
            runCatching { adminUserRepository.deleteById(mcpAdmin.id) }
        }
    }

    private fun jwtFor(adminUser: AdminUser): String {
        val principal = AdminUserPrincipal(adminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }
}
