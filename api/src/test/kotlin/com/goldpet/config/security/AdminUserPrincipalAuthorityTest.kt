package com.goldpet.config.security

import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AdminUserPrincipalAuthorityTest {

    private fun principalFor(role: AdminUserRole): AdminUserPrincipal {
        val adminUser = AdminUser(
            email = "admin@example.com",
            passwordHash = "bcrypt-hash",
            name = "Test Admin",
            role = role
        )
        return AdminUserPrincipal(adminUser)
    }

    @Test
    fun `SUPER_ADMIN role yields ROLE_SUPER_ADMIN authority`() {
        val authorities = principalFor(AdminUserRole.SUPER_ADMIN).getAuthorities().map { it.authority }
        assertEquals(listOf("ROLE_SUPER_ADMIN"), authorities)
    }

    @Test
    fun `OPERATOR role yields ROLE_OPERATOR authority`() {
        val authorities = principalFor(AdminUserRole.OPERATOR).getAuthorities().map { it.authority }
        assertEquals(listOf("ROLE_OPERATOR"), authorities)
    }

    @Test
    fun `VIEWER role yields ROLE_VIEWER authority`() {
        val authorities = principalFor(AdminUserRole.VIEWER).getAuthorities().map { it.authority }
        assertEquals(listOf("ROLE_VIEWER"), authorities)
    }

    @Test
    fun `authority must not contain literal dollar-sign interpolation (C1 regression lock)`() {
        // Before the C1 fix, AdminUserPrincipal.kt:13 had a backslash before the dollar sign:
        //   SimpleGrantedAuthority("ROLE_\${adminUser.role.name}")
        // This made the authority the literal 40-char string "ROLE_${adminUser.role.name}" instead
        // of the interpolated value. This test locks out that regression.
        val authority = principalFor(AdminUserRole.SUPER_ADMIN).getAuthorities().first().authority
        assertFalse(authority.contains("\${"), "Authority must not contain literal '\${' — backslash-escape C1 bug detected")
        assertFalse(authority.contains("role.name"), "Authority must not contain 'role.name' literal — broken template string")
        assertFalse(authority.length > 30, "Authority is suspiciously long (${authority.length} chars) — possible literal template string")
    }

    @Test
    fun `each role yields exactly one authority`() {
        AdminUserRole.entries.forEach { role ->
            val count = principalFor(role).getAuthorities().size
            assertEquals(1, count, "Expected 1 authority for role $role, got $count")
        }
    }
}
