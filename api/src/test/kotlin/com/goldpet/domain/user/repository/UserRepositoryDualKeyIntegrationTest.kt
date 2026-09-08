package com.goldpet.domain.user.repository

import com.goldpet.IntegrationTestBase
import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.admin.service.AdminAuditService
import com.goldpet.domain.admin.service.PiiKeyRotationService
import com.goldpet.domain.user.entity.User
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
 * Exercises findByEmailHash — the load-bearing OAuth2 lookup path — across dual-key states.
 *
 * This path is called from 7 login sites (AuthService, SocialLoginService,
 * CustomOAuth2UserService). Failure after rotation means all 21 TestFlight testers
 * cannot log in.
 *
 * Test matrix:
 *  - Before rotation: user's PII is secondary-key ciphertext, email_hash under primary HMAC.
 *    findByEmailHash MUST find the user (hash matches; JPA decrypts via secondary fallback).
 *  - After rotation: PII is primary-key ciphertext, email_hash recomputed (same HMAC key).
 *    findByEmailHash MUST still find the user.
 */
@Tag("integration")
class UserRepositoryDualKeyIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var piiKeyRotationService: PiiKeyRotationService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    // admin_audit_logs.action is VARCHAR(20); service writes 24-char string — mock to avoid overflow.
    @MockBean private lateinit var adminAuditService: AdminAuditService

    private val PRIMARY = "test-encryption-key-32-chars!!!"
    private val SECONDARY = "test-secondary-key-32-chars!!!!"

    private val ts = System.currentTimeMillis()
    private val email = "urdk_$ts@goldpet.com"
    private var seededUserId: Long = -1L

    @BeforeEach
    fun setUp() {
        // Seed user with secondary-key PII ciphertext but primary-key email_hash.
        val user = userRepository.save(User(
            id = 0,
            email = email,
            oauthProvider = "KAKAO",
            oauthId = "urdk_kakao_$ts",
            username = "urdk_$ts",
            password = null,
            nickname = "urdk_$ts",
            name = "URDK",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        ))
        seededUserId = user.id

        // Overwrite email with secondary-key ciphertext, set email_hash = HMAC(email, primary).
        jdbcTemplate.update(
            "UPDATE users SET email = ?, email_hash = ? WHERE id = ?",
            encryptWith(email, SECONDARY),
            computeHmac(email, PRIMARY),
            seededUserId
        )
    }

    @AfterEach
    fun tearDown() {
        runCatching { userRepository.deleteById(seededUserId) }
    }

    @Test
    fun `findByEmailHash finds user before rotation — secondary-key ciphertext dual-key readable`() {
        val hash = BlindIndexUtil.hash(email)!!
        val found = userRepository.findByEmailHash(hash)
        assertTrue(found.isPresent, "findByEmailHash must find user even with secondary-key ciphertext")
        assertEquals(seededUserId, found.get().id)
    }

    @Test
    fun `JPA can read decrypted email before rotation via secondary-key fallback`() {
        val user = userRepository.findById(seededUserId).orElseThrow()
        assertEquals(email, user.email,
            "EncryptionConverter dual-key fallback must decrypt secondary-key ciphertext transparently")
    }

    @Test
    fun `findByEmailHash finds user after rotation`() {
        piiKeyRotationService.rotate(dryRun = false, principalId = -1L)

        val hash = BlindIndexUtil.hash(email)!!
        val found = userRepository.findByEmailHash(hash)
        assertTrue(found.isPresent, "findByEmailHash must still find user after rotation")
        assertEquals(seededUserId, found.get().id)
    }

    @Test
    fun `JPA can read decrypted email after rotation — now via primary-key ciphertext`() {
        piiKeyRotationService.rotate(dryRun = false, principalId = -1L)

        val user = userRepository.findById(seededUserId).orElseThrow()
        assertEquals(email, user.email,
            "Email must remain readable after rotation via primary-key ciphertext")
    }

    @Test
    fun `email_hash value is stable across rotation — same HMAC key before and after`() {
        val hashBefore = BlindIndexUtil.hash(email)!!
        piiKeyRotationService.rotate(dryRun = false, principalId = -1L)
        val hashAfter = BlindIndexUtil.hash(email)!!
        assertEquals(hashBefore, hashAfter,
            "BlindIndexUtil always uses primary key so hash must be identical pre/post rotation")
    }

    // ── helpers ────────────────────────────────────────────────────────────────

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
