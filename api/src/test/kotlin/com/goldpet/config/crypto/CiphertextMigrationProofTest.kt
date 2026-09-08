package com.goldpet.config.crypto

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.admin.service.AdminAuditService
import com.goldpet.domain.admin.service.PiiKeyRotationService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * R2 Option C recipe — proves that after rotation, every row's ciphertext is under the
 * new primary key ALONE, and that the dual-key fallback is NOT masking a failed rotation.
 *
 * Spring Boot tests do not cleanly support mid-test property swaps, so instead of
 * @DirtiesContext + context restart, we construct a standalone primary-only decrypt helper
 * programmatically and feed it raw ciphertext pulled via JdbcTemplate.
 *
 * Steps:
 *  1. Seed 3 users whose PII columns are encrypted under the SECONDARY key (via JdbcTemplate update).
 *  2. Rotate: invoke PiiKeyRotationService.rotate(). Assert rotated=3, failed=0.
 *  3. Verify under primary-only helper: pull raw ciphertext via JdbcTemplate, decode with
 *     primary key only, assert every result matches the expected plaintext.
 *     — If any row still holds old-key ciphertext, primaryOnlyDecrypt returns null → assertion fails.
 */
@Tag("integration")
class CiphertextMigrationProofTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var piiKeyRotationService: PiiKeyRotationService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    // Mock to keep audit writes out of the hot path for this proof-of-ciphertext test.
    @MockBean private lateinit var adminAuditService: AdminAuditService

    private val PRIMARY = "test-encryption-key-32-chars!!!"
    private val SECONDARY = "test-secondary-key-32-chars!!!!"

    private val ts = System.currentTimeMillis()
    private val seededUserIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        // Create 3 users via JPA (encrypted with primary), then overwrite PII with secondary-key ciphertext.
        repeat(3) { i ->
            val email = "cmpt_$i$ts@goldpet.com"
            val user = userRepository.save(User(
                id = 0,
                email = email,
                oauthProvider = "LOCAL",
                oauthId = "cmpt_$i$ts",
                username = "cmpt_$i$ts",
                password = passwordEncoder.encode("pass"),
                nickname = "cmpt_$i$ts",
                name = "Name$i",
                birthDate = null,
                phoneNumber = null,
                gender = null,
                birthYear = null,
                mainLocationText = null,
                mainLocationGeom = null,
                profileImageUrl = null
            ))
            seededUserIds.add(user.id)

            // Overwrite email/name with secondary-key ciphertext so rotation has work to do.
            // email_hash stays under primary HMAC (BlindIndexUtil always uses primary key).
            jdbcTemplate.update(
                "UPDATE users SET email = ?, name = ?, email_hash = ? WHERE id = ?",
                encryptWith(email, SECONDARY),
                encryptWith("Name$i", SECONDARY),
                computeHmac(email, PRIMARY),
                user.id
            )
        }
    }

    @AfterEach
    fun tearDown() {
        seededUserIds.forEach { id -> runCatching { userRepository.deleteById(id) } }
    }

    @Test
    fun `rotation re-encrypts all secondary-key rows under primary and verification pass matches`() {
        val result = piiKeyRotationService.rotate(dryRun = false, principalId = -1L)

        // Leftover secondary-key rows from prior test runs may also be rotated.
        assertTrue(result.rotated >= 3, "Expected at least 3 rows rotated")
        assertEquals(0, result.failed, "Expected 0 failures; errors: ${result.mismatches}")
        assertTrue(result.verificationPass.mismatches.isEmpty(),
            "Verification pass mismatches: ${result.verificationPass.mismatches}")
    }

    @Test
    fun `after rotation raw ciphertext in DB decrypts with primary key only`() {
        piiKeyRotationService.rotate(dryRun = false, principalId = -1L)

        // Pull raw ciphertext directly from DB — bypassing JPA converters (dual-key).
        val rows = jdbcTemplate.queryForList(
            "SELECT id, email FROM users WHERE id = ANY(?)",
            seededUserIds.toTypedArray()
        )

        assertEquals(3, rows.size)
        for (row in rows) {
            val id = row["id"] as Long
            val rawCiphertext = row["email"] as String

            // Primary-only decrypt — if rotation left old-key ciphertext, this returns null.
            val plaintext = primaryOnlyDecrypt(rawCiphertext)
            assertNotNull(plaintext, "userId=$id: primary-only decrypt returned null — row was NOT rotated")
            assertTrue(plaintext!!.contains("@goldpet.com"),
                "userId=$id: decrypted value '$plaintext' does not look like a seeded email")

            // Confirm it CANNOT be decrypted by secondary alone (would mean old-key is masking).
            // Secondary ciphertext encrypted under primary will NOT decrypt with secondary → returns null.
            // Note: this is a best-effort check; AES-GCM AEAD will reject mismatched keys with an exception.
            val withSecondaryOnly = secondaryOnlyDecrypt(rawCiphertext)
            assertNull(withSecondaryOnly,
                "userId=$id: secondary-only decrypt succeeded — rotation may not have run for this row")
        }
    }

    @Test
    fun `dry run reports rotated=3 without committing any writes`() {
        val dryResult = piiKeyRotationService.rotate(dryRun = true, principalId = -1L)
        assertEquals(3, dryResult.rotated)
        assertTrue(dryResult.dryRun)

        // Raw ciphertext should still be decryptable by secondary (no writes happened).
        val rows = jdbcTemplate.queryForList(
            "SELECT email FROM users WHERE id = ANY(?)",
            seededUserIds.toTypedArray()
        )
        for (row in rows) {
            val rawCiphertext = row["email"] as String
            val withSecondary = secondaryOnlyDecrypt(rawCiphertext)
            assertNotNull(withSecondary, "Dry-run wrote changes — but it should not have")
        }
    }

    // ── standalone primary-only / secondary-only helpers ──────────────────────
    // These are intentionally NOT Spring beans. They replicate AES-GCM decrypt with a single key,
    // so the test cannot be fooled by the dual-key converter falling back to secondary.

    private fun primaryOnlyDecrypt(ciphertext: String): String? = decryptWith(ciphertext, PRIMARY)
    private fun secondaryOnlyDecrypt(ciphertext: String): String? = decryptWith(ciphertext, SECONDARY)

    private fun decryptWith(dbData: String, keyString: String): String? {
        return try {
            val decoded = Base64.getDecoder().decode(dbData)
            if (decoded.size < 29) return null
            val iv = decoded.sliceArray(0 until 12)
            val encrypted = decoded.sliceArray(12 until decoded.size)
            val keyBytes = keyString.toByteArray(Charsets.UTF_8).copyOf(32)
            val key = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun encryptWith(plaintext: String, keyString: String): String {
        val keyBytes = keyString.toByteArray(Charsets.UTF_8).copyOf(32)
        val key = SecretKeySpec(keyBytes, "AES")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    private fun computeHmac(plaintext: String, keyString: String): String {
        val keyBytes = keyString.toByteArray(Charsets.UTF_8).copyOf(32)
        val key = SecretKeySpec(keyBytes, "HmacSHA256")
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(key)
        return Base64.getEncoder().encodeToString(mac.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }
}
