package com.goldpet.domain.admin.audit

import com.goldpet.domain.admin.entity.AdminUserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [AdminAuditDetails] sealed type 의 Jackson polymorphic round-trip 검증.
 *
 * - 각 variant: encode → decode → 동일 인스턴스
 * - V60 이전 row 의 plain string `details` 컬럼 → [AdminAuditDetails.Unknown] fallback
 * - 깨진 JSON / 미등록 _type → Unknown fallback
 * - null/blank → null
 */
class AdminAuditDetailsRoundTripTest {

    @Test
    fun `RoleChange round-trip preserves before and after roles`() {
        val original = AdminAuditDetails.RoleChange(
            before = AdminUserRole.OPERATOR,
            after = AdminUserRole.SUPER_ADMIN
        )
        val json = AdminAuditDetailsCodec.encode(original)
        assertTrue(json!!.contains("\"_type\":\"RoleChange\""))
        val decoded = AdminAuditDetailsCodec.decode(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `Backup round-trip preserves nullable fields`() {
        val original = AdminAuditDetails.Backup(
            filePath = "/var/backups/2026-05-04.sql.gz",
            sizeBytes = 1_234_567L
        )
        val json = AdminAuditDetailsCodec.encode(original)
        val decoded = AdminAuditDetailsCodec.decode(json)
        assertEquals(original, decoded)

        val nullsOriginal = AdminAuditDetails.Backup(filePath = null, sizeBytes = null)
        val nullsDecoded = AdminAuditDetailsCodec.decode(AdminAuditDetailsCodec.encode(nullsOriginal))
        assertEquals(nullsOriginal, nullsDecoded)
    }

    @Test
    fun `PasswordReset round-trip preserves target identifier`() {
        val original = AdminAuditDetails.PasswordReset(target = "admin@goldpet.com")
        val json = AdminAuditDetailsCodec.encode(original)
        val decoded = AdminAuditDetailsCodec.decode(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `PlaceUpdate round-trip preserves all coordinates`() {
        val original = AdminAuditDetails.PlaceUpdate(
            beforeLat = 37.5665,
            afterLat = 37.5670,
            beforeLon = 126.9780,
            afterLon = 126.9785
        )
        val decoded = AdminAuditDetailsCodec.decode(AdminAuditDetailsCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `decode legacy plain string falls back to Unknown with raw payload`() {
        val legacy = "Backup completed at 2026-05-04T10:00:00"
        val decoded = AdminAuditDetailsCodec.decode(legacy)
        assertEquals(AdminAuditDetails.Unknown(raw = legacy), decoded)
    }

    @Test
    fun `decode malformed JSON falls back to Unknown with raw payload`() {
        val malformed = "{not_real_json"
        val decoded = AdminAuditDetailsCodec.decode(malformed)
        assertEquals(AdminAuditDetails.Unknown(raw = malformed), decoded)
    }

    @Test
    fun `decode unknown _type falls back to Unknown via defaultImpl`() {
        // _type 디스크리미네이터는 있으나 sealed 변형으로 등록되지 않은 케이스
        val unknownType = "{\"_type\":\"NewlyAddedActionNotYetDeployed\",\"foo\":\"bar\"}"
        val decoded = AdminAuditDetailsCodec.decode(unknownType)
        assertTrue(decoded is AdminAuditDetails.Unknown)
    }

    @Test
    fun `null and blank inputs decode to null`() {
        assertNull(AdminAuditDetailsCodec.decode(null))
        assertNull(AdminAuditDetailsCodec.decode(""))
        assertNull(AdminAuditDetailsCodec.decode("   "))
    }

    @Test
    fun `null details encode to null`() {
        assertNull(AdminAuditDetailsCodec.encode(null))
    }

    @Test
    fun `Unknown round-trip preserves raw payload`() {
        val original = AdminAuditDetails.Unknown(raw = "legacy free text")
        val json = AdminAuditDetailsCodec.encode(original)
        val decoded = AdminAuditDetailsCodec.decode(json)
        assertEquals(original, decoded)
    }

    // ── AdminCRUD 후 audit log row deserialize 검증 ────────────────────────────

    @Test
    fun `AdminCreate round-trip preserves role and email`() {
        val original = AdminAuditDetails.AdminCreate(role = AdminUserRole.OPERATOR, email = "new@goldpet.com")
        val json = AdminAuditDetailsCodec.encode(original)!!
        assertTrue(json.contains("\"_type\":\"AdminCreate\""))
        assertTrue(json.contains("\"role\":\"OPERATOR\""))
        assertTrue(json.contains("\"email\":\"new@goldpet.com\""))
        val decoded = AdminAuditDetailsCodec.decode(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `AdminDelete round-trip preserves role and isActive`() {
        val original = AdminAuditDetails.AdminDelete(role = AdminUserRole.SUPER_ADMIN, isActive = false)
        val json = AdminAuditDetailsCodec.encode(original)!!
        assertTrue(json.contains("\"_type\":\"AdminDelete\""))
        val decoded = AdminAuditDetailsCodec.decode(json)
        assertEquals(original, decoded)
        assertEquals(AdminUserRole.SUPER_ADMIN, (decoded as AdminAuditDetails.AdminDelete).role)
    }

    @Test
    fun `Failure round-trip preserves reason`() {
        val original = AdminAuditDetails.Failure(reason = "Conflict: LAST_SUPER_ADMIN")
        val json = AdminAuditDetailsCodec.encode(original)!!
        assertTrue(json.contains("\"_type\":\"Failure\""))
        val decoded = AdminAuditDetailsCodec.decode(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `ADMIN_CREATE AdminCreate JSON contains _type discriminator and role field`() {
        val details = AdminAuditDetails.AdminCreate(role = AdminUserRole.OPERATOR, email = "op@goldpet.com")
        val json = AdminAuditDetailsCodec.encode(details)!!
        assertTrue(json.contains("\"_type\":\"AdminCreate\""))
        assertTrue(json.contains("\"role\":\"OPERATOR\""))
        assertTrue(json.contains("\"email\":\"op@goldpet.com\""))
    }

    @Test
    fun `ADMIN_PASSWORD_RESET PasswordReset JSON contains _type discriminator and target field`() {
        val adminId = 42L
        val details = AdminAuditDetails.PasswordReset(target = adminId.toString())
        val json = AdminAuditDetailsCodec.encode(details)!!
        assertTrue(json.contains("\"_type\":\"PasswordReset\""))
        assertTrue(json.contains("\"target\":\"$adminId\""))
    }

    @Test
    fun `ADMIN_DELETE AdminDelete for SUPER_ADMIN decode round-trip preserves role`() {
        val original = AdminAuditDetails.AdminDelete(role = AdminUserRole.SUPER_ADMIN, isActive = false)
        val decoded = AdminAuditDetailsCodec.decode(AdminAuditDetailsCodec.encode(original))
        assertEquals(original, decoded)
        assertTrue(decoded is AdminAuditDetails.AdminDelete)
        assertEquals(AdminUserRole.SUPER_ADMIN, (decoded as AdminAuditDetails.AdminDelete).role)
    }
}
