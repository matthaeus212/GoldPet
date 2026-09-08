package com.goldpet.domain.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.audit.AdminAuditDetails
import com.goldpet.domain.admin.audit.AdminAuditDetailsCodec
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Admin CRUD 핵심 + audit log JSON contract 검증.
 *
 * CREATE: 201 / temporaryPassword / mustChangePassword=true in DB / ADMIN_CREATE audit RoleChange JSON
 * UPDATE: 200 / ADMIN_UPDATE audit RoleChange JSON with correct after-role / 자기보호(역할·비활성) 400
 * DELETE: 204 soft-delete(isActive=false) / ADMIN_DELETE audit ADMIN_USER target / 자기보호 400 / OPERATOR 403
 * RESET_PASSWORD: 200 / mustChangePassword=true / ADMIN_PASSWORD_RESET audit PasswordReset JSON / OPERATOR 403
 */
@AutoConfigureMockMvc
@Tag("integration")
class AdminCrudTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper

    private val ts = System.currentTimeMillis()
    private lateinit var superAdmin: AdminUser
    private lateinit var targetOperator: AdminUser

    @BeforeEach
    fun setUp() {
        superAdmin = adminUserRepository.save(AdminUser(
            email = "crud_super_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Crud Super",
            role = AdminUserRole.SUPER_ADMIN,
            isActive = true
        ))
        targetOperator = adminUserRepository.save(AdminUser(
            email = "crud_operator_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Crud Operator",
            role = AdminUserRole.OPERATOR,
            isActive = true
        ))
    }

    @AfterEach
    fun tearDown() {
        runCatching { adminUserRepository.deleteById(superAdmin.id) }
        runCatching { adminUserRepository.deleteById(targetOperator.id) }
    }

    // ── CREATE ────────────────────────────────────────────────────────────────────

    @Test
    fun `SUPER_ADMIN can create admin - 201 with temporaryPassword in response`() {
        val email = "new_admin_$ts@goldpet.com"
        val result = mockMvc.perform(
            post("/api/v1/admin/system/admins")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","name":"New Admin","role":"OPERATOR"}""")
        ).andExpect(status().isCreated)
         .andExpect(jsonPath("$.id").isNumber)
         .andExpect(jsonPath("$.temporaryPassword").isString)
         .andReturn()

        val id = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        runCatching { adminUserRepository.deleteById(id) }
    }

    @Test
    fun `created admin has mustChangePassword=true in DB`() {
        val email = "mcp_new_$ts@goldpet.com"
        val result = mockMvc.perform(
            post("/api/v1/admin/system/admins")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","name":"MCP Admin","role":"OPERATOR"}""")
        ).andExpect(status().isCreated).andReturn()

        val id = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        try {
            val mustChange = jdbcTemplate.queryForObject(
                "SELECT must_change_password FROM admin_users WHERE id = ?",
                Boolean::class.java, id
            )!!
            assertThat(mustChange).isTrue()
        } finally {
            runCatching { adminUserRepository.deleteById(id) }
        }
    }

    @Test
    fun `ADMIN_CREATE audit log row has AdminCreate JSON details deserializable to AdminCreate`() {
        val email = "audit_create_$ts@goldpet.com"
        val result = mockMvc.perform(
            post("/api/v1/admin/system/admins")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","name":"Audit Admin","role":"OPERATOR"}""")
        ).andExpect(status().isCreated).andReturn()

        val id = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        try {
            val rows = jdbcTemplate.queryForList(
                """SELECT details FROM admin_audit_logs
                   WHERE action = 'ADMIN_CREATE' AND target_id = ? AND details IS NOT NULL
                   ORDER BY created_at DESC LIMIT 1""",
                id
            )
            assertThat(rows).isNotEmpty
            val decoded = AdminAuditDetailsCodec.decode(rows.first()["details"] as? String)
            assertThat(decoded).isInstanceOf(AdminAuditDetails.AdminCreate::class.java)
            assertThat((decoded as AdminAuditDetails.AdminCreate).role).isEqualTo(AdminUserRole.OPERATOR)
        } finally {
            runCatching { adminUserRepository.deleteById(id) }
        }
    }

    @Test
    fun `OPERATOR cannot create admin - 403`() {
        mockMvc.perform(
            post("/api/v1/admin/system/admins")
                .header("Authorization", "Bearer ${jwtFor(targetOperator)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"op_blocked_$ts@goldpet.com","name":"Blocked","role":"OPERATOR"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `duplicate email returns 409`() {
        mockMvc.perform(
            post("/api/v1/admin/system/admins")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${superAdmin.email}","name":"Dup","role":"OPERATOR"}""")
        ).andExpect(status().isConflict)
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────────

    @Test
    fun `SUPER_ADMIN can update admin name - 200 with updated name`() {
        mockMvc.perform(
            put("/api/v1/admin/system/admins/${targetOperator.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Renamed Operator"}""")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.name").value("Renamed Operator"))
    }

    @Test
    fun `ADMIN_UPDATE audit log has RoleChange details with correct after-role`() {
        mockMvc.perform(
            put("/api/v1/admin/system/admins/${targetOperator.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"VIEWER"}""")
        ).andExpect(status().isOk)

        val rows = jdbcTemplate.queryForList(
            """SELECT details FROM admin_audit_logs
               WHERE action = 'ADMIN_UPDATE' AND target_id = ? AND details IS NOT NULL
               ORDER BY created_at DESC LIMIT 1""",
            targetOperator.id
        )
        assertThat(rows).isNotEmpty
        val decoded = AdminAuditDetailsCodec.decode(rows.first()["details"] as? String)
        assertThat(decoded).isInstanceOf(AdminAuditDetails.RoleChange::class.java)
        assertThat((decoded as AdminAuditDetails.RoleChange).after).isEqualTo(AdminUserRole.VIEWER)
    }

    @Test
    fun `SUPER_ADMIN cannot change own role - 400 self-protection`() {
        mockMvc.perform(
            put("/api/v1/admin/system/admins/${superAdmin.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"OPERATOR"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `SUPER_ADMIN cannot deactivate own account - 400 self-protection`() {
        mockMvc.perform(
            put("/api/v1/admin/system/admins/${superAdmin.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"isActive":false}""")
        ).andExpect(status().isBadRequest)
    }

    // ── DELETE ────────────────────────────────────────────────────────────────────

    @Test
    fun `SUPER_ADMIN soft-deletes admin - 204 and isActive becomes false in DB`() {
        val target = adminUserRepository.save(AdminUser(
            email = "delete_target_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Delete Target",
            role = AdminUserRole.OPERATOR,
            isActive = true
        ))
        try {
            mockMvc.perform(
                delete("/api/v1/admin/system/admins/${target.id}")
                    .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
            ).andExpect(status().isNoContent)

            val isActive = jdbcTemplate.queryForObject(
                "SELECT is_active FROM admin_users WHERE id = ?",
                Boolean::class.java, target.id
            )!!
            assertThat(isActive).isFalse()
        } finally {
            runCatching { adminUserRepository.deleteById(target.id) }
        }
    }

    @Test
    fun `ADMIN_DELETE audit log has target_type=ADMIN_USER and target_id set`() {
        val target = adminUserRepository.save(AdminUser(
            email = "delete_audit_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Delete Audit",
            role = AdminUserRole.OPERATOR,
            isActive = true
        ))
        try {
            mockMvc.perform(
                delete("/api/v1/admin/system/admins/${target.id}")
                    .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
            ).andExpect(status().isNoContent)

            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE action = 'ADMIN_DELETE' AND target_type = 'ADMIN_USER' AND target_id = ?",
                Long::class.java, target.id
            )!!
            assertThat(count).isGreaterThan(0)
        } finally {
            runCatching { adminUserRepository.deleteById(target.id) }
        }
    }

    @Test
    fun `SUPER_ADMIN cannot delete own account - 400 self-protection`() {
        mockMvc.perform(
            delete("/api/v1/admin/system/admins/${superAdmin.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `OPERATOR cannot delete admin - 403`() {
        mockMvc.perform(
            delete("/api/v1/admin/system/admins/${targetOperator.id}")
                .header("Authorization", "Bearer ${jwtFor(targetOperator)}")
        ).andExpect(status().isForbidden)
    }

    // ── RESET PASSWORD ────────────────────────────────────────────────────────────

    @Test
    fun `SUPER_ADMIN resets password - 200 with temporaryPassword and sets mustChangePassword=true`() {
        mockMvc.perform(
            post("/api/v1/admin/system/admins/${targetOperator.id}/reset-password")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$.temporaryPassword").isString)

        val mustChange = jdbcTemplate.queryForObject(
            "SELECT must_change_password FROM admin_users WHERE id = ?",
            Boolean::class.java, targetOperator.id
        )!!
        assertThat(mustChange).isTrue()
    }

    @Test
    fun `ADMIN_PASSWORD_RESET audit log has PasswordReset JSON details`() {
        mockMvc.perform(
            post("/api/v1/admin/system/admins/${targetOperator.id}/reset-password")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
        ).andExpect(status().isOk)

        val rows = jdbcTemplate.queryForList(
            """SELECT details FROM admin_audit_logs
               WHERE action = 'ADMIN_PASSWORD_RESET' AND target_id = ? AND details IS NOT NULL
               ORDER BY created_at DESC LIMIT 1""",
            targetOperator.id
        )
        assertThat(rows).isNotEmpty
        val decoded = AdminAuditDetailsCodec.decode(rows.first()["details"] as? String)
        assertThat(decoded).isInstanceOf(AdminAuditDetails.PasswordReset::class.java)
    }

    @Test
    fun `OPERATOR cannot reset password - 403`() {
        mockMvc.perform(
            post("/api/v1/admin/system/admins/${targetOperator.id}/reset-password")
                .header("Authorization", "Bearer ${jwtFor(targetOperator)}")
        ).andExpect(status().isForbidden)
    }

    // ── advisory lock / count query syntax safety ─────────────────────────────────

    @Test
    fun `demoting non-last SUPER_ADMIN succeeds - FOR UPDATE row-fetch query is syntax-safe`() {
        val second = adminUserRepository.save(AdminUser(
            email = "lock_test_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Lock Test Super",
            role = AdminUserRole.SUPER_ADMIN,
            isActive = true
        ))
        try {
            // Exercises acquireSuperAdminLock() + countActiveSuperAdminsForUpdate().
            // count=2 so demotion is allowed; verifies no PostgreSQL syntax error.
            mockMvc.perform(
                put("/api/v1/admin/system/admins/${second.id}")
                    .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""{"role":"OPERATOR"}""")
            ).andExpect(status().isOk)
        } finally {
            runCatching { adminUserRepository.deleteById(second.id) }
        }
    }

    // ── audit dedup guard ─────────────────────────────────────────────────────────

    @Test
    fun `@CriticalAction endpoint - interceptor does not add duplicate generic CREATE row`() {
        val beforeSec = System.currentTimeMillis() / 1000.0
        val email = "nodedup_$ts@goldpet.com"
        val result = mockMvc.perform(
            post("/api/v1/admin/system/admins")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","name":"NoDup","role":"OPERATOR"}""")
        ).andExpect(status().isCreated).andReturn()

        val id = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        try {
            // Interceptor maps POST → action='CREATE'. With @CriticalAction skip it must produce 0 such rows.
            val dupCount = jdbcTemplate.queryForObject(
                """SELECT COUNT(*) FROM admin_audit_logs
                   WHERE admin_user_id = ? AND action = 'CREATE'
                   AND created_at >= to_timestamp(?)""",
                Long::class.java, superAdmin.id, beforeSec
            )!!
            assertThat(dupCount).isEqualTo(0)
        } finally {
            runCatching { adminUserRepository.deleteById(id) }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────────

    private fun jwtFor(adminUser: AdminUser): String {
        val principal = AdminUserPrincipal(adminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }
}
