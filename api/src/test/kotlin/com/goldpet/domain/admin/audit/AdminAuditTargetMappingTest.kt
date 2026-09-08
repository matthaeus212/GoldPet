package com.goldpet.domain.admin.audit

import com.goldpet.domain.admin.entity.AdminUserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AuditTarget 매핑 규칙 + AdminCRUD 액션별 details 타입 계약 검증.
 *
 * - AuditTarget type/id 필드 정확성 및 nullable id
 * - ADMIN_CREATE / ADMIN_UPDATE → RoleChange details _type 계약
 * - ADMIN_PASSWORD_RESET → PasswordReset details _type + target 필드 계약
 * - requestPath 에서 관리자 ID 를 추출하는 regex 패턴 (`/admins/{id}`) 검증
 * - 비-admin path 에 regex 가 매칭되지 않음 확인
 */
class AdminAuditTargetMappingTest {

    // ── AuditTarget 구조 ──────────────────────────────────────────────────────────

    @Test
    fun `AuditTarget stores type and id correctly`() {
        val target = AuditTarget("ADMIN_USER", 42L)
        assertEquals("ADMIN_USER", target.type)
        assertEquals(42L, target.id)
    }

    @Test
    fun `AuditTarget id defaults to null when omitted`() {
        val target = AuditTarget("ADMIN_USER")
        assertEquals("ADMIN_USER", target.type)
        assertNull(target.id)
    }

    // ── AdminCRUD details 타입 계약 ──────────────────────────────────────────────

    @Test
    fun `ADMIN_CREATE uses AdminCreate details with _type discriminator`() {
        val details = AdminAuditDetails.AdminCreate(role = AdminUserRole.OPERATOR, email = "new@goldpet.com")
        val json = AdminAuditDetailsCodec.encode(details)!!
        assertTrue(json.contains("\"_type\":\"AdminCreate\""), "Missing _type:AdminCreate in: $json")
        val decoded = AdminAuditDetailsCodec.decode(json)
        assertTrue(decoded is AdminAuditDetails.AdminCreate, "Expected AdminCreate but got: ${decoded?.javaClass}")
    }

    @Test
    fun `ADMIN_UPDATE uses RoleChange details with _type discriminator`() {
        listOf(
            AdminAuditDetails.RoleChange(before = AdminUserRole.OPERATOR, after = AdminUserRole.SUPER_ADMIN),
            AdminAuditDetails.RoleChange(before = AdminUserRole.SUPER_ADMIN, after = AdminUserRole.SUPER_ADMIN)
        ).forEach { details ->
            val json = AdminAuditDetailsCodec.encode(details)!!
            assertTrue(json.contains("\"_type\":\"RoleChange\""), "Missing _type:RoleChange in: $json")
            val decoded = AdminAuditDetailsCodec.decode(json)
            assertTrue(decoded is AdminAuditDetails.RoleChange, "Expected RoleChange but got: ${decoded?.javaClass}")
        }
    }

    @Test
    fun `ADMIN_PASSWORD_RESET uses PasswordReset details with target as admin id string`() {
        val adminId = 99L
        val details = AdminAuditDetails.PasswordReset(target = adminId.toString())
        val json = AdminAuditDetailsCodec.encode(details)!!
        assertTrue(json.contains("\"_type\":\"PasswordReset\""))
        assertTrue(json.contains("\"target\":\"$adminId\""))
        val decoded = AdminAuditDetailsCodec.decode(json)
        assertTrue(decoded is AdminAuditDetails.PasswordReset)
        assertEquals(adminId.toString(), (decoded as AdminAuditDetails.PasswordReset).target)
    }

    // ── requestPath 에서 관리자 ID 추출 regex ────────────────────────────────────

    @Test
    fun `admin path regex extracts id from various admins paths`() {
        val regex = Regex("""/admins/(\d+)""")
        mapOf(
            "/api/v1/admin/system/admins/42" to 42L,
            "/api/v1/admin/system/admins/1" to 1L,
            "/api/v1/admin/system/admins/999/reset-password" to 999L
        ).forEach { (path, expectedId) ->
            val extracted = regex.find(path)?.groupValues?.get(1)?.toLong()
            assertEquals(expectedId, extracted, "Failed to extract id from path: $path")
        }
    }

    @Test
    fun `admin path regex does not match non-admin-crud paths`() {
        val regex = Regex("""/admins/(\d+)""")
        listOf(
            "/api/v1/admin/system/info",
            "/api/v1/admin/lbs/places/42",
            "/api/v1/admin/system/maintenance",
            "/api/v1/admin/system/admins"
        ).forEach { path ->
            assertNull(regex.find(path), "Should not match path: $path")
        }
    }
}
