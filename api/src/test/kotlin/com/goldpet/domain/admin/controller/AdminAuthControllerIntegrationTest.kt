package com.goldpet.domain.admin.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.IntegrationTestBase
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@AutoConfigureMockMvc
@Tag("integration")
class AdminAuthControllerIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var adminUserRepository: AdminUserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var testAdminUser: AdminUser

    private val testEmails = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        // Create test users with unique emails
        testAdminUser = adminUserRepository.save(
            AdminUser(
                email = "integration_test_${System.currentTimeMillis()}@goldpet.com",
                passwordHash = passwordEncoder.encode("testPassword123"),
                name = "통합테스트관리자",
                role = AdminUserRole.SUPER_ADMIN,
                otpSecret = null,
                isActive = true
            )
        )
        testEmails.add(testAdminUser.email)
    }

    @AfterEach
    fun tearDown() {
        // Clean up test data — BlindIndex 해시로 조회 (email 컬럼이 이제 암호화되어 equality 불가)
        testEmails.forEach { email ->
            val hash = com.goldpet.config.crypto.BlindIndexUtil.hash(email) ?: return@forEach
            adminUserRepository.findByEmailHash(hash).ifPresent {
                adminUserRepository.delete(it)
            }
        }
        testEmails.clear()
    }

    @Test
    fun `login should return token for valid credentials without 2FA`() {
        val request = AdminLoginRequest(
            email = testAdminUser.email,
            password = "testPassword123"
        )

        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requiresTwoFactor").value(false))
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value(testAdminUser.email))
            .andExpect(jsonPath("$.user.name").value("통합테스트관리자"))
            .andExpect(jsonPath("$.user.role").value("SUPER_ADMIN"))
    }

    @Test
    fun `login should return 401 for invalid email`() {
        val request = AdminLoginRequest(
            email = "invalid_nonexistent@goldpet.com",
            password = "testPassword123"
        )

        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login should return 401 for invalid password`() {
        val request = AdminLoginRequest(
            email = testAdminUser.email,
            password = "wrongPassword"
        )

        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login should return 401 for disabled account`() {
        val disabledEmail = "disabled_${System.currentTimeMillis()}@goldpet.com"
        testEmails.add(disabledEmail)

        adminUserRepository.save(
            AdminUser(
                email = disabledEmail,
                passwordHash = passwordEncoder.encode("testPassword123"),
                name = "비활성관리자",
                role = AdminUserRole.OPERATOR,
                otpSecret = null,
                isActive = false
            )
        )

        val request = AdminLoginRequest(
            email = disabledEmail,
            password = "testPassword123"
        )

        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login should require 2FA when otp_secret is set`() {
        val twoFaEmail = "2fa_${System.currentTimeMillis()}@goldpet.com"
        testEmails.add(twoFaEmail)

        val userWith2FA = adminUserRepository.save(
            AdminUser(
                email = twoFaEmail,
                passwordHash = passwordEncoder.encode("testPassword123"),
                name = "2FA관리자",
                role = AdminUserRole.SUPER_ADMIN,
                otpSecret = "JBSWY3DPEHPK3PXP",
                isActive = true
            )
        )

        val request = AdminLoginRequest(
            email = twoFaEmail,
            password = "testPassword123"
        )

        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requiresTwoFactor").value(true))
            .andExpect(jsonPath("$.token").isEmpty)
            .andExpect(jsonPath("$.user.id").value(userWith2FA.id))
            .andExpect(jsonPath("$.user.isTwoFactorEnabled").value(true))
    }

    @Test
    fun `verify2fa should return 401 for invalid code`() {
        val verify2faEmail = "verify2fa_${System.currentTimeMillis()}@goldpet.com"
        testEmails.add(verify2faEmail)

        adminUserRepository.save(
            AdminUser(
                email = verify2faEmail,
                passwordHash = passwordEncoder.encode("testPassword123"),
                name = "2FA검증관리자",
                role = AdminUserRole.SUPER_ADMIN,
                otpSecret = "JBSWY3DPEHPK3PXP",
                isActive = true
            )
        )

        // EXT-CDX-005: verify-2fa 는 login 이 발급한 challengeId 에 바인딩된다.
        val loginResult = mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(AdminLoginRequest(verify2faEmail, "testPassword123")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.twoFactorChallengeId").isNotEmpty)
            .andReturn()
        val challengeId = objectMapper.readTree(loginResult.response.contentAsString)
            .get("twoFactorChallengeId").asText()

        val request = Verify2faRequest(challengeId = challengeId, code = 0)

        mockMvc.perform(
            post("/api/v1/admin/auth/verify-2fa")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `verify2fa should reject direct call without a challenge`() {
        // EXT-CDX-005: challenge 없이 임의 challengeId 로 직접 호출 → 401(우회 차단).
        val request = Verify2faRequest(challengeId = "no-such-challenge", code = 123456)

        mockMvc.perform(
            post("/api/v1/admin/auth/verify-2fa")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `setup2fa should require authentication`() {
        mockMvc.perform(
            post("/api/v1/admin/auth/2fa/setup")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `confirm2fa should require authentication`() {
        val request = Confirm2faRequest(
            secret = "TESTSECRET",
            code = 123456
        )

        mockMvc.perform(
            post("/api/v1/admin/auth/2fa/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `remove2fa should require authentication`() {
        mockMvc.perform(
            delete("/api/v1/admin/auth/2fa")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `login response should include correct role for OPERATOR`() {
        val operatorEmail = "operator_${System.currentTimeMillis()}@goldpet.com"
        testEmails.add(operatorEmail)

        adminUserRepository.save(
            AdminUser(
                email = operatorEmail,
                passwordHash = passwordEncoder.encode("testPassword123"),
                name = "운영자",
                role = AdminUserRole.OPERATOR,
                otpSecret = null,
                isActive = true
            )
        )

        val request = AdminLoginRequest(
            email = operatorEmail,
            password = "testPassword123"
        )

        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.role").value("OPERATOR"))
    }

    @Test
    fun `login response should include correct role for VIEWER`() {
        val viewerEmail = "viewer_${System.currentTimeMillis()}@goldpet.com"
        testEmails.add(viewerEmail)

        adminUserRepository.save(
            AdminUser(
                email = viewerEmail,
                passwordHash = passwordEncoder.encode("testPassword123"),
                name = "뷰어",
                role = AdminUserRole.VIEWER,
                otpSecret = null,
                isActive = true
            )
        )

        val request = AdminLoginRequest(
            email = viewerEmail,
            password = "testPassword123"
        )

        mockMvc.perform(
            post("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.role").value("VIEWER"))
    }
}
