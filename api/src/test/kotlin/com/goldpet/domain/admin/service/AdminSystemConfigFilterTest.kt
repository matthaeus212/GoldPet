package com.goldpet.domain.admin.service

import com.goldpet.IntegrationTestBase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * security LOW#2: GET /api/v1/admin/system/configs has no method-level role gate, so any VIEWER
 * could read `experiment.compatibility.salt` in cleartext and predict A/B cohorts. The fix filters
 * `.salt` / `.salt_version` keys out of [AdminSystemService.getAllConfigs] at the service layer.
 */
@Tag("integration")
class AdminSystemConfigFilterTest : IntegrationTestBase() {

    @Autowired private lateinit var adminSystemService: AdminSystemService
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val saltKey = "experiment.compatibility.salt"
    private val saltVersionKey = "experiment.compatibility.salt_version"
    private val safeKey = "experiment.compatibility.enabled"

    @BeforeEach
    fun seed() {
        upsert(saltKey, "topsecretsalt")
        upsert(saltVersionKey, "3")
        upsert(safeKey, "true")
    }

    @AfterEach
    fun cleanup() {
        jdbcTemplate.update(
            "DELETE FROM system_settings WHERE setting_key IN (?, ?, ?)",
            saltKey, saltVersionKey, safeKey,
        )
    }

    private fun upsert(key: String, value: String) {
        jdbcTemplate.update(
            """
            INSERT INTO system_settings (setting_key, setting_value) VALUES (?, ?)
            ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value
            """.trimIndent(),
            key, value,
        )
    }

    @Test
    fun `getAllConfigs hides salt keys but keeps non-sensitive experiment keys`() {
        val keys = adminSystemService.getAllConfigs().map { it.key }

        assertFalse(keys.any { it.endsWith(".salt") }, "salt keys must be filtered out")
        assertFalse(keys.any { it.endsWith(".salt_version") }, "salt_version keys must be filtered out")
        assertTrue(keys.contains(safeKey), "non-sensitive config keys must remain visible")
    }
}
