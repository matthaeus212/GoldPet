package com.goldpet.domain.admin

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.UserBadge
import com.goldpet.domain.gamification.repository.BadgeRepository
import com.goldpet.domain.gamification.repository.UserBadgeRepository
import com.goldpet.domain.user.repository.UserRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * P1.3 뱃지 DELETE 소프트/하드 분기 검증.
 *
 * - 획득 유저 없음 → 하드 삭제 (HTTP 204, DB 에서 완전 제거)
 * - 획득 유저 있음 → 소프트 삭제 (HTTP 200, isActive=false, DB 잔존)
 * - 존재하지 않는 ID → HTTP 404
 */
@AutoConfigureMockMvc
@Tag("integration")
class AdminBadgeDeleteTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var badgeRepository: BadgeRepository
    @Autowired private lateinit var userBadgeRepository: UserBadgeRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val ts = System.currentTimeMillis()
    private lateinit var superAdminUser: AdminUser
    private val createdBadgeIds = mutableListOf<Long>()
    private val createdUserIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        superAdminUser = adminUserRepository.save(AdminUser(
            email = "badge_super_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "Badge SuperAdmin",
            role = AdminUserRole.SUPER_ADMIN,
            otpSecret = null,
            isActive = true
        ))
    }

    @AfterEach
    fun tearDown() {
        createdBadgeIds.forEach { id ->
            runCatching { jdbcTemplate.update("DELETE FROM user_badges WHERE badge_id = ?", id) }
            runCatching { badgeRepository.deleteById(id) }
        }
        createdUserIds.forEach { id -> runCatching { userRepository.deleteById(id) } }
        runCatching { adminUserRepository.delete(superAdminUser) }
    }

    private fun createBadge(name: String): Badge {
        val badge = badgeRepository.save(Badge(
            name = name,
            description = "Test badge",
            imageUrl = ""
        ))
        createdBadgeIds.add(badge.id)
        return badge
    }

    private fun createMinimalUser(): Long {
        val userId = jdbcTemplate.queryForObject(
            """INSERT INTO users (oauth_provider, oauth_id, created_at, updated_at)
               VALUES ('LOCAL', 'badge_test_$ts', NOW(), NOW())
               RETURNING id""",
            Long::class.java
        )!!
        createdUserIds.add(userId)
        return userId
    }

    private fun jwtFor(adminUser: AdminUser): String {
        val principal = AdminUserPrincipal(adminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }

    // ── Hard delete (no earned users → 204) ──────────────────────────────────────────────

    @Test
    fun `DELETE badge with no earned users performs hard delete (204)`() {
        val badge = createBadge("하드삭제뱃지_$ts")

        mockMvc.perform(
            delete("/api/v1/admin/gamification/badges/${badge.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
        ).andExpect(status().isNoContent)

        val inDb = badgeRepository.findById(badge.id)
        assertThat(inDb).isEmpty
        createdBadgeIds.remove(badge.id)
    }

    // ── Soft delete (earned users exist → 200, isActive=false) ───────────────────────────

    @Test
    fun `DELETE badge with earned users performs soft delete (200, isActive=false)`() {
        val badge = createBadge("소프트삭제뱃지_$ts")
        val userId = createMinimalUser()
        val user = userRepository.findById(userId).orElseThrow()
        userBadgeRepository.save(UserBadge(user = user, badge = badge))

        mockMvc.perform(
            delete("/api/v1/admin/gamification/badges/${badge.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
        ).andExpect(status().isOk)

        val inDb = badgeRepository.findById(badge.id).orElse(null)
        assertThat(inDb).isNotNull
        assertThat(inDb!!.isActive).isFalse
    }

    // ── Not found (→ 404) ─────────────────────────────────────────────────────────────────

    @Test
    fun `DELETE non-existent badge returns 404`() {
        mockMvc.perform(
            delete("/api/v1/admin/gamification/badges/999999999")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
        ).andExpect(status().isNotFound)
    }
}
