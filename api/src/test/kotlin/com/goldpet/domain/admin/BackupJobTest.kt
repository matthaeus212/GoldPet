package com.goldpet.domain.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.audit.AdminAuditDetails
import com.goldpet.domain.admin.audit.AdminAuditDetailsCodec
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.entity.BackupJob
import com.goldpet.domain.admin.entity.BackupJobStatus
import com.goldpet.domain.admin.entity.BackupTriggerType
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.admin.repository.BackupJobRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * BackupJob 엔드포인트 통합 테스트.
 *
 * 1. SUPER_ADMIN POST → 202 (status=PENDING, triggerType=MANUAL)
 * 2. OPERATOR POST → 403
 * 3. PENDING 존재 시 POST → 409
 * 4. RUNNING 존재 시 POST → 409
 * 5. SUPER_ADMIN GET /backup/jobs → 200 + 배열
 * 6. OPERATOR GET /backup/jobs → 200 (클래스 레벨 허용)
 * 7. VIEWER GET /backup/jobs → 403
 * 8. POST 후 audit log: action='BACKUP_TRIGGER' + Backup details 행 적재
 */
@AutoConfigureMockMvc
@Tag("integration")
class BackupJobTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var backupJobRepository: BackupJobRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var objectMapper: ObjectMapper

    private val ts = System.currentTimeMillis()
    private lateinit var superAdmin: AdminUser
    private lateinit var operator: AdminUser
    private lateinit var viewer: AdminUser
    private val seededJobIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        superAdmin = adminUserRepository.save(AdminUser(
            email = "backup_super_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Backup SuperAdmin",
            role = AdminUserRole.SUPER_ADMIN,
            isActive = true
        ))
        operator = adminUserRepository.save(AdminUser(
            email = "backup_op_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Backup Operator",
            role = AdminUserRole.OPERATOR,
            isActive = true
        ))
        viewer = adminUserRepository.save(AdminUser(
            email = "backup_viewer_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Backup Viewer",
            role = AdminUserRole.VIEWER,
            isActive = true
        ))
    }

    @AfterEach
    fun tearDown() {
        seededJobIds.forEach { id -> runCatching { backupJobRepository.deleteById(id) } }
        seededJobIds.clear()
        runCatching {
            jdbcTemplate.update(
                "DELETE FROM backup_jobs WHERE triggered_by_admin_id IN (?, ?, ?)",
                superAdmin.id, operator.id, viewer.id
            )
        }
        runCatching { adminUserRepository.deleteById(superAdmin.id) }
        runCatching { adminUserRepository.deleteById(operator.id) }
        runCatching { adminUserRepository.deleteById(viewer.id) }
    }

    // ── Case 1: SUPER_ADMIN POST → 202 PENDING MANUAL ────────────────────────────

    @Test
    fun `SUPER_ADMIN triggers backup - 202 with PENDING MANUAL response`() {
        val result = mockMvc.perform(
            post("/api/v1/admin/system/backup")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
        ).andExpect(status().isAccepted)
         .andExpect(jsonPath("$.id").isNumber)
         .andExpect(jsonPath("$.status").value("PENDING"))
         .andExpect(jsonPath("$.triggerType").value("MANUAL"))
         .andReturn()

        val jobId = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        seededJobIds.add(jobId)
    }

    // ── Case 2: OPERATOR POST → 403 ──────────────────────────────────────────────

    @Test
    fun `OPERATOR cannot trigger backup - 403`() {
        mockMvc.perform(
            post("/api/v1/admin/system/backup")
                .header("Authorization", "Bearer ${jwtFor(operator)}")
        ).andExpect(status().isForbidden)
    }

    // ── Case 3: PENDING 존재 → 409 ───────────────────────────────────────────────

    @Test
    fun `POST backup when PENDING job exists returns 409`() {
        val pending = backupJobRepository.save(BackupJob(
            status = BackupJobStatus.PENDING,
            triggerType = BackupTriggerType.AUTO_CRON
        ))
        seededJobIds.add(pending.id)

        mockMvc.perform(
            post("/api/v1/admin/system/backup")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
        ).andExpect(status().isConflict)
    }

    // ── Case 4: RUNNING 존재 → 409 ───────────────────────────────────────────────

    @Test
    fun `POST backup when RUNNING job exists returns 409`() {
        val running = backupJobRepository.save(BackupJob(
            status = BackupJobStatus.RUNNING,
            triggerType = BackupTriggerType.AUTO_CRON
        ))
        seededJobIds.add(running.id)

        mockMvc.perform(
            post("/api/v1/admin/system/backup")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
        ).andExpect(status().isConflict)
    }

    // ── Case 5: SUPER_ADMIN GET /backup/jobs → 200 ───────────────────────────────

    @Test
    fun `SUPER_ADMIN lists backup jobs - 200 with array`() {
        mockMvc.perform(
            get("/api/v1/admin/system/backup/jobs")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$").isArray)
    }

    // ── Case 6: OPERATOR GET /backup/jobs → 200 ──────────────────────────────────

    @Test
    fun `OPERATOR lists backup jobs - 200`() {
        mockMvc.perform(
            get("/api/v1/admin/system/backup/jobs")
                .header("Authorization", "Bearer ${jwtFor(operator)}")
        ).andExpect(status().isOk)
         .andExpect(jsonPath("$").isArray)
    }

    // ── Case 7: VIEWER GET /backup/jobs → 403 ────────────────────────────────────

    @Test
    fun `VIEWER cannot list backup jobs - 403`() {
        mockMvc.perform(
            get("/api/v1/admin/system/backup/jobs")
                .header("Authorization", "Bearer ${jwtFor(viewer)}")
        ).andExpect(status().isForbidden)
    }

    // ── Case 8: Audit log after POST ─────────────────────────────────────────────

    @Test
    fun `POST backup writes BACKUP_TRIGGER audit log with Backup details`() {
        val result = mockMvc.perform(
            post("/api/v1/admin/system/backup")
                .header("Authorization", "Bearer ${jwtFor(superAdmin)}")
        ).andExpect(status().isAccepted).andReturn()

        val jobId = objectMapper.readTree(result.response.contentAsString)["id"].asLong()
        seededJobIds.add(jobId)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_logs WHERE action = 'BACKUP_TRIGGER' AND admin_user_id = ?",
            Long::class.java, superAdmin.id
        )!!
        assertThat(count).isGreaterThanOrEqualTo(1)

        val rows = jdbcTemplate.queryForList(
            """SELECT details FROM admin_audit_logs
               WHERE action = 'BACKUP_TRIGGER' AND target_type = 'BACKUP_JOB' AND target_id = ?
                 AND details IS NOT NULL
               ORDER BY created_at DESC LIMIT 1""",
            jobId
        )
        assertThat(rows).isNotEmpty
        val decoded = AdminAuditDetailsCodec.decode(rows.first()["details"] as? String)
        assertThat(decoded).isInstanceOf(AdminAuditDetails.Backup::class.java)
    }

    // ── helper ────────────────────────────────────────────────────────────────────

    private fun jwtFor(adminUser: AdminUser): String {
        val principal = AdminUserPrincipal(adminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }
}
