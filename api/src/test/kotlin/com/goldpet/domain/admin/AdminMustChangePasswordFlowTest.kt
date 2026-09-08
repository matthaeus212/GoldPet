package com.goldpet.domain.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * mustChangePassword 강제 변경 흐름 (V61 필드 + change-password 엔드포인트) 검증.
 *
 * - 신규 관리자 로그인 시 AdminLoginResponse.mustChangePassword=true
 * - 로그인 후 lastLoginAt 갱신
 * - POST /api/v1/admin/auth/change-password → mustChangePassword=false
 * - 잘못된 현재 비밀번호 → 401
 */
@AutoConfigureMockMvc
@Tag("integration")
class AdminMustChangePasswordFlowTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper

    private val ts = System.currentTimeMillis()
    private val rawPassword = "TestPass123!"
    private lateinit var newAdmin: AdminUser

    @BeforeEach
    fun setUp() {
        newAdmin = adminUserRepository.save(AdminUser(
            email = "mcp_flow_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode(rawPassword),
            name = "MCP Flow Admin",
            role = AdminUserRole.OPERATOR,
            isActive = true,
            mustChangePassword = true
        ))
    }

    @AfterEach
    fun tearDown() {
        runCatching { adminUserRepository.deleteById(newAdmin.id) }
    }

    @Test
    fun `login response contains mustChangePassword=true for newly created admin`() {
        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"mcp_flow_$ts@goldpet.com","password":"$rawPassword"}""")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.mustChangePassword").value(true))
         .andExpect(jsonPath("$.token").isString)
    }

    @Test
    fun `login updates lastLoginAt in DB`() {
        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"mcp_flow_$ts@goldpet.com","password":"$rawPassword"}""")
        ).andExpect(status().isOk)

        val lastLoginAt = jdbcTemplate.queryForObject(
            "SELECT last_login_at FROM admin_users WHERE id = ?",
            java.sql.Timestamp::class.java, newAdmin.id
        )
        assertThat(lastLoginAt).isNotNull()
    }

    @Test
    fun `changePassword clears mustChangePassword flag`() {
        val loginResult = mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"mcp_flow_$ts@goldpet.com","password":"$rawPassword"}""")
        ).andReturn()
        val token = objectMapper.readTree(loginResult.response.contentAsString)["token"].asText()

        mockMvc.perform(
            post("/api/v1/admin/auth/change-password")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"$rawPassword","newPassword":"NewPass456@"}""")
        ).andExpect(status().isNoContent)

        val mustChange = jdbcTemplate.queryForObject(
            "SELECT must_change_password FROM admin_users WHERE id = ?",
            Boolean::class.java, newAdmin.id
        )!!
        assertThat(mustChange).isFalse()
    }

    @Test
    fun `changePassword with wrong current password returns 401`() {
        val token = jwtFor(newAdmin)
        mockMvc.perform(
            post("/api/v1/admin/auth/change-password")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"WrongPass999!","newPassword":"NewPass456@"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `changePassword rejects password shorter than 8 characters`() {
        val token = jwtFor(newAdmin)
        mockMvc.perform(
            post("/api/v1/admin/auth/change-password")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"$rawPassword","newPassword":"Ab1!"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `changePassword rejects password without special character`() {
        val token = jwtFor(newAdmin)
        mockMvc.perform(
            post("/api/v1/admin/auth/change-password")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"$rawPassword","newPassword":"TestPass123"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `changePassword rejects password without digit`() {
        val token = jwtFor(newAdmin)
        mockMvc.perform(
            post("/api/v1/admin/auth/change-password")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"$rawPassword","newPassword":"TestPass!!!"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `changePassword rejects password without letter`() {
        val token = jwtFor(newAdmin)
        mockMvc.perform(
            post("/api/v1/admin/auth/change-password")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword":"$rawPassword","newPassword":"12345678!"}""")
        ).andExpect(status().isBadRequest)
    }

    private fun jwtFor(adminUser: AdminUser): String {
        val principal = AdminUserPrincipal(adminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }
}
