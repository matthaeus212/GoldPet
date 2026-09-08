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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Phase 0 권한 매트릭스 검증.
 *
 * Phase 0.A: hasAnyRole('ADMIN','SUPER_ADMIN') → hasAnyRole('SUPER_ADMIN','OPERATOR','VIEWER') 정정
 *   검증 엔드포인트: GET /api/v1/admin/users (AdminUserManagementController)
 *
 * Phase 0.B: AdminLBSController / AdminSystemController 클래스 레벨 가드 존재 smoke
 *   검증 엔드포인트: GET /api/v1/admin/lbs/places
 *
 * F5: VIEWER read-only 매트릭스 — VIEWER 는 모든 GET 허용, write 차단.
 */
@AutoConfigureMockMvc
@Tag("integration")
class AdminAuthorizationMatrixTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val ts = System.currentTimeMillis()
    private lateinit var superAdminUser: AdminUser
    private lateinit var operatorUser: AdminUser
    private lateinit var viewerUser: AdminUser

    @BeforeEach
    fun setUp() {
        superAdminUser = adminUserRepository.save(
            AdminUser(
                email = "matrix_super_$ts@goldpet.com",
                passwordHash = passwordEncoder.encode("pass"),
                name = "Matrix SuperAdmin",
                role = AdminUserRole.SUPER_ADMIN,
                otpSecret = null,
                isActive = true
            )
        )
        operatorUser = adminUserRepository.save(
            AdminUser(
                email = "matrix_operator_$ts@goldpet.com",
                passwordHash = passwordEncoder.encode("pass"),
                name = "Matrix Operator",
                role = AdminUserRole.OPERATOR,
                otpSecret = null,
                isActive = true
            )
        )
        viewerUser = adminUserRepository.save(
            AdminUser(
                email = "matrix_viewer_$ts@goldpet.com",
                passwordHash = passwordEncoder.encode("pass"),
                name = "Matrix Viewer",
                role = AdminUserRole.VIEWER,
                otpSecret = null,
                isActive = true
            )
        )
    }

    @AfterEach
    fun tearDown() {
        runCatching { adminUserRepository.delete(superAdminUser) }
        runCatching { adminUserRepository.delete(operatorUser) }
        runCatching { adminUserRepository.delete(viewerUser) }
    }

    // ── Phase 0.A: AdminUserManagementController GET /api/v1/admin/users ──────

    @Test
    fun `Phase0A - OPERATOR can access admin users endpoint`() {
        mockMvc.perform(
            get("/api/v1/admin/users")
                .header("Authorization", "Bearer ${jwtFor(operatorUser)}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `F5 - VIEWER can access admin users GET endpoint (read-only matrix)`() {
        mockMvc.perform(
            get("/api/v1/admin/users")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `Phase0A - unauthenticated request to admin users returns 401`() {
        mockMvc.perform(get("/api/v1/admin/users"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `Phase0A - SUPER_ADMIN can access admin users endpoint`() {
        mockMvc.perform(
            get("/api/v1/admin/users")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
        ).andExpect(status().isOk)
    }

    // ── Phase 0.B: AdminLBSController GET /api/v1/admin/lbs/places ──────────

    @Test
    fun `Phase0B - OPERATOR can access LBS places endpoint`() {
        mockMvc.perform(
            get("/api/v1/admin/lbs/places")
                .header("Authorization", "Bearer ${jwtFor(operatorUser)}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `F5 - VIEWER can access LBS places GET endpoint (read-only matrix)`() {
        mockMvc.perform(
            get("/api/v1/admin/lbs/places")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
        ).andExpect(status().isOk)
    }

    // ── F5: VIEWER read-only 매트릭스 — write 차단 ─────────────────────────────

    @Test
    fun `F5 - VIEWER cannot POST to admin LBS places (write blocked)`() {
        mockMvc.perform(
            post("/api/v1/admin/lbs/places")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"test","category":"PARK","latitude":37.5,"longitude":127.0}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `F5 - VIEWER cannot PUT to admin LBS places (write blocked)`() {
        mockMvc.perform(
            put("/api/v1/admin/lbs/places/99999")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"renamed"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `F5 - VIEWER cannot DELETE admin LBS place (write blocked)`() {
        mockMvc.perform(
            delete("/api/v1/admin/lbs/places/99999")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `F5 - VIEWER cannot POST gamification badge (write blocked)`() {
        mockMvc.perform(
            post("/api/v1/admin/gamification/badges")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"test","description":"d","imageUrl":null,"conditionType":null,"conditionValue":null,"rewardGold":null,"startDate":null,"endDate":null,"isRepeatable":null,"repeatCycle":null}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `F5 - VIEWER cannot PATCH community post (write blocked)`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/community/posts/99999")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"x"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `F5 - VIEWER cannot DELETE community post (write blocked)`() {
        mockMvc.perform(
            delete("/api/v1/admin/community/posts/99999")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `F5 - VIEWER cannot POST report resolve (write blocked)`() {
        mockMvc.perform(
            post("/api/v1/admin/reports/99999/resolve")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"actionType":"WARN_USER"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `F5 - VIEWER can GET dashboard stats (read-only matrix)`() {
        mockMvc.perform(
            get("/api/v1/admin/dashboard/stats")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `F5 - VIEWER can GET system info (read-only matrix)`() {
        mockMvc.perform(
            get("/api/v1/admin/system/info")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
        ).andExpect(status().isOk)
    }

    @Test
    fun `F5 - VIEWER can GET gamification badges (read-only matrix)`() {
        mockMvc.perform(
            get("/api/v1/admin/gamification/badges")
                .header("Authorization", "Bearer ${jwtFor(viewerUser)}")
        ).andExpect(status().isOk)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun jwtFor(adminUser: AdminUser): String {
        val principal = AdminUserPrincipal(adminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }
}
