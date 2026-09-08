package com.goldpet.domain.admin

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.checkin.entity.Place
import com.goldpet.domain.checkin.entity.PlaceCategory
import com.goldpet.domain.checkin.repository.PlaceRepository
import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.repository.BadgeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Phase 1 권한 매트릭스 검증 (P1.6 + P1.7 + P1.8).
 *
 * P1.6: 클래스-레벨은 OPERATOR 허용이지만 특정 메서드는 SUPER_ADMIN 전용.
 *   검증: POST /api/v1/admin/system/maintenance, POST /api/v1/admin/economy/adjust
 *
 * P1.7: LBS / Gamification DELETE 메서드 레벨 가드.
 *   검증: DELETE /api/v1/admin/lbs/places/{id}, DELETE /api/v1/admin/gamification/badges/{id}
 *
 * P1.8: OPERATOR 가 SUPER_ADMIN 전용 endpoint 접근 시 ACCESS_DENIED audit log 적재.
 */
@AutoConfigureMockMvc
@Tag("integration")
class AdminAuthorizationMatrixPhase1Test : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var placeRepository: PlaceRepository
    @Autowired private lateinit var badgeRepository: BadgeRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val ts = System.currentTimeMillis()
    private lateinit var superAdminUser: AdminUser
    private lateinit var operatorUser: AdminUser
    private lateinit var viewerUser: AdminUser
    private val gf = GeometryFactory(PrecisionModel(), 4326)

    @BeforeEach
    fun setUp() {
        superAdminUser = adminUserRepository.save(AdminUser(
            email = "p1m_super_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "P1M SuperAdmin",
            role = AdminUserRole.SUPER_ADMIN,
            otpSecret = null,
            isActive = true
        ))
        operatorUser = adminUserRepository.save(AdminUser(
            email = "p1m_operator_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "P1M Operator",
            role = AdminUserRole.OPERATOR,
            otpSecret = null,
            isActive = true
        ))
        viewerUser = adminUserRepository.save(AdminUser(
            email = "p1m_viewer_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "P1M Viewer",
            role = AdminUserRole.VIEWER,
            otpSecret = null,
            isActive = true
        ))
    }

    @AfterEach
    fun tearDown() {
        runCatching { adminUserRepository.delete(superAdminUser) }
        runCatching { adminUserRepository.delete(operatorUser) }
        runCatching { adminUserRepository.delete(viewerUser) }
    }

    // ── P1.6: POST /api/v1/admin/system/maintenance ───────────────────────────────────────

    @Test
    fun `P1_6 - SUPER_ADMIN can toggle maintenance mode`() {
        mockMvc.perform(
            post("/api/v1/admin/system/maintenance")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":false}""")
        ).andExpect(status().isOk)
    }

    @Test
    fun `P1_6 - OPERATOR is forbidden from setting maintenance mode (method-level guard)`() {
        mockMvc.perform(
            post("/api/v1/admin/system/maintenance")
                .header("Authorization", "Bearer ${jwtFor(operatorUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":false}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `P1_6 - VIEWER is forbidden from setting maintenance mode (method-level SUPER_ADMIN guard)`() {
        // F5: VIEWER 가 클래스 레벨 가드는 통과하지만 메서드 레벨 SUPER_ADMIN-only override 에서 차단됨.
        mockMvc.perform(
            post("/api/v1/admin/system/maintenance")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":false}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `P1_6 - unauthenticated request to maintenance returns 401`() {
        mockMvc.perform(
            post("/api/v1/admin/system/maintenance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":false}""")
        ).andExpect(status().isUnauthorized)
    }

    // ── P1.6: POST /api/v1/admin/economy/adjust ───────────────────────────────────────────

    @Test
    fun `P1_6 - OPERATOR is forbidden from adjusting gold (method-level guard)`() {
        mockMvc.perform(
            post("/api/v1/admin/economy/adjust")
                .header("Authorization", "Bearer ${jwtFor(operatorUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"amount":100,"reason":"test"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `P1_6 - VIEWER is forbidden from adjusting gold (method-level SUPER_ADMIN guard)`() {
        // F5: VIEWER 가 클래스 레벨 가드는 통과하지만 메서드 레벨 SUPER_ADMIN-only override 에서 차단됨.
        mockMvc.perform(
            post("/api/v1/admin/economy/adjust")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":1,"amount":100,"reason":"test"}""")
        ).andExpect(status().isForbidden)
    }

    // ── P1.7: DELETE /api/v1/admin/lbs/places/{id} ───────────────────────────────────────

    @Test
    fun `P1_7 - SUPER_ADMIN can delete LBS place`() {
        val place = placeRepository.save(Place(
            name = "삭제테스트장소_$ts",
            category = PlaceCategory.PARK,
            locationGeom = gf.createPoint(Coordinate(126.977, 37.566))
        ))
        try {
            mockMvc.perform(
                delete("/api/v1/admin/lbs/places/${place.id}")
                    .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
            ).andExpect(status().isNoContent)
        } finally {
            runCatching { placeRepository.deleteById(place.id) }
        }
    }

    @Test
    fun `P1_7 - OPERATOR is forbidden from deleting LBS place (method-level guard)`() {
        mockMvc.perform(
            delete("/api/v1/admin/lbs/places/99999")
                .header("Authorization", "Bearer ${jwtFor(operatorUser)}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `P1_7 - VIEWER is forbidden from deleting LBS place (method-level SUPER_ADMIN guard)`() {
        // F5: VIEWER 가 클래스 레벨 가드는 통과하지만 메서드 레벨 SUPER_ADMIN-only override 에서 차단됨.
        mockMvc.perform(
            delete("/api/v1/admin/lbs/places/99999")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
        ).andExpect(status().isForbidden)
    }

    // ── P1.7: DELETE /api/v1/admin/gamification/badges/{id} ─────────────────────────────

    @Test
    fun `P1_7 - SUPER_ADMIN can delete badge`() {
        val badge = badgeRepository.save(Badge(
            name = "삭제테스트뱃지_$ts",
            description = "Test badge",
            imageUrl = ""
        ))
        try {
            mockMvc.perform(
                delete("/api/v1/admin/gamification/badges/${badge.id}")
                    .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
            ).andExpect(status().isNoContent)
        } finally {
            runCatching { badgeRepository.deleteById(badge.id) }
        }
    }

    @Test
    fun `P1_7 - OPERATOR is forbidden from deleting badge (method-level guard)`() {
        mockMvc.perform(
            delete("/api/v1/admin/gamification/badges/99999")
                .header("Authorization", "Bearer ${jwtFor(operatorUser)}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `P1_7 - VIEWER is forbidden from deleting badge (method-level SUPER_ADMIN guard)`() {
        // F5: VIEWER 가 클래스 레벨 가드는 통과하지만 메서드 레벨 SUPER_ADMIN-only override 에서 차단됨.
        mockMvc.perform(
            delete("/api/v1/admin/gamification/badges/99999")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
        ).andExpect(status().isForbidden)
    }

    // ── P1.8: ACCESS_DENIED audit log ─────────────────────────────────────────────────────

    @Test
    fun `P1_8 - ACCESS_DENIED is audited when OPERATOR hits SUPER_ADMIN-only endpoint`() {
        mockMvc.perform(
            post("/api/v1/admin/system/maintenance")
                .header("Authorization", "Bearer ${jwtFor(operatorUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":false}""")
        ).andExpect(status().isForbidden)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_audit_logs WHERE action = 'ACCESS_DENIED' AND admin_user_id = ?",
            Long::class.java,
            operatorUser.id
        )!!
        assertThat(count).isGreaterThan(0)
    }

    @Test
    fun `P1_8 - audit log path contains the blocked endpoint`() {
        mockMvc.perform(
            post("/api/v1/admin/system/maintenance")
                .header("Authorization", "Bearer ${jwtFor(operatorUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":false}""")
        ).andExpect(status().isForbidden)

        val rows = jdbcTemplate.queryForList(
            "SELECT request_path FROM admin_audit_logs WHERE action = 'ACCESS_DENIED' AND admin_user_id = ? ORDER BY created_at DESC LIMIT 1",
            operatorUser.id
        )
        assertThat(rows).isNotEmpty
        assertThat(rows.first()["request_path"] as String).contains("/api/v1/admin/system/maintenance")
    }

    // ── P1.6+: PUT /api/v1/admin/users/{id}/unlock-profile ──────────────────────────────

    @Test
    fun `OPERATOR cannot unlock user profile (SUPER_ADMIN-only)`() {
        mockMvc.perform(
            put("/api/v1/admin/users/{id}/unlock-profile", 99999L)
                .header("Authorization", "Bearer ${jwtFor(operatorUser)}")
        ).andExpect(status().isForbidden)
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────

    private fun jwtFor(adminUser: AdminUser): String {
        val principal = AdminUserPrincipal(adminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }
}
