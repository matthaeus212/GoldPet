package com.goldpet.service.oauth2

import com.goldpet.IntegrationTestBase
import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.admin.service.AdminAuditService
import com.goldpet.domain.admin.service.PiiKeyRotationService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
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
 * M2 fix: exercises the OAuth2 login path's email-hash lookup both BEFORE and AFTER rotation.
 *
 * All 21 TestFlight testers log in via Kakao/Naver/Google/Apple OAuth2. The critical path is:
 *   SocialLoginService.kt:256 → userRepository.findByEmailHash(BlindIndexUtil.hash(email))
 *
 * If the email_hash or dual-key decrypt is broken after rotation, every tester login fails.
 * This test seeds an OAuth2-style user (oauthProvider=KAKAO) with secondary-key ciphertext
 * and confirms the hash lookup path survives rotation end-to-end.
 */
@Tag("integration")
class OAuth2HashLookupIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var piiKeyRotationService: PiiKeyRotationService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    // admin_audit_logs.action is VARCHAR(20); service writes 24-char string — mock to avoid overflow.
    @MockBean private lateinit var adminAuditService: AdminAuditService

    private val PRIMARY = "test-encryption-key-32-chars!!!"
    private val SECONDARY = "test-secondary-key-32-chars!!!!"

    private val ts = System.currentTimeMillis()
    private val kakaoEmail = "oauth2_$ts@kakao.com"
    private val naverEmail = "oauth2_naver_$ts@naver.com"
    private var kakaoUserId: Long = -1L
    private var naverUserId: Long = -1L

    @BeforeEach
    fun setUp() {
        // Kakao user — secondary-key ciphertext (simulates pre-rotation state)
        val kakaoUser = userRepository.save(User(
            id = 0,
            email = kakaoEmail,
            oauthProvider = "KAKAO",
            oauthId = "kakao_$ts",
            username = null,
            password = null,
            nickname = "kakao_$ts",
            name = "KakaoUser",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        ))
        kakaoUserId = kakaoUser.id
        jdbcTemplate.update(
            "UPDATE users SET email = ?, email_hash = ? WHERE id = ?",
            encryptWith(kakaoEmail, SECONDARY),
            computeHmac(kakaoEmail, PRIMARY),
            kakaoUserId
        )

        // Naver user — also secondary-key ciphertext
        val naverUser = userRepository.save(User(
            id = 0,
            email = naverEmail,
            oauthProvider = "NAVER",
            oauthId = "naver_$ts",
            username = null,
            password = null,
            nickname = "naver_$ts",
            name = "NaverUser",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        ))
        naverUserId = naverUser.id
        jdbcTemplate.update(
            "UPDATE users SET email = ?, email_hash = ? WHERE id = ?",
            encryptWith(naverEmail, SECONDARY),
            computeHmac(naverEmail, PRIMARY),
            naverUserId
        )
    }

    @AfterEach
    fun tearDown() {
        runCatching { userRepository.deleteById(kakaoUserId) }
        runCatching { userRepository.deleteById(naverUserId) }
    }

    // ── pre-rotation ───────────────────────────────────────────────────────────

    @Test
    fun `Kakao OAuth2 hash lookup succeeds BEFORE rotation — dual-key decrypt fallback works`() {
        val hash = BlindIndexUtil.hash(kakaoEmail)!!
        val found = userRepository.findByEmailHash(hash)
        assertTrue(found.isPresent, "Kakao user must be found via email_hash pre-rotation")
        assertEquals(kakaoUserId, found.get().id)
    }

    @Test
    fun `Naver OAuth2 hash lookup succeeds BEFORE rotation`() {
        val hash = BlindIndexUtil.hash(naverEmail)!!
        val found = userRepository.findByEmailHash(hash)
        assertTrue(found.isPresent, "Naver user must be found via email_hash pre-rotation")
        assertEquals(naverUserId, found.get().id)
    }

    // ── post-rotation ──────────────────────────────────────────────────────────

    @Test
    fun `Kakao OAuth2 hash lookup succeeds AFTER rotation — primary-key ciphertext and hash intact`() {
        piiKeyRotationService.rotate(dryRun = false, principalId = -1L)

        val hash = BlindIndexUtil.hash(kakaoEmail)!!
        val found = userRepository.findByEmailHash(hash)
        assertTrue(found.isPresent, "Kakao user must be found via email_hash after rotation")
        assertEquals(kakaoUserId, found.get().id)
        assertEquals(kakaoEmail, found.get().email,
            "Decrypted email must match original after rotation to primary key")
    }

    @Test
    fun `Naver OAuth2 hash lookup succeeds AFTER rotation`() {
        piiKeyRotationService.rotate(dryRun = false, principalId = -1L)

        val hash = BlindIndexUtil.hash(naverEmail)!!
        val found = userRepository.findByEmailHash(hash)
        assertTrue(found.isPresent, "Naver user must be found via email_hash after rotation")
        assertEquals(naverUserId, found.get().id)
    }

    @Test
    fun `hash value is identical before and after rotation — no login disruption`() {
        val beforeHash = BlindIndexUtil.hash(kakaoEmail)!!
        piiKeyRotationService.rotate(dryRun = false, principalId = -1L)
        val afterHash = BlindIndexUtil.hash(kakaoEmail)!!

        assertEquals(beforeHash, afterHash,
            "HMAC key is always primary — hash value must be stable across rotation")
    }

    @Test
    fun `findByEmailHash with wrong hash returns empty — no cross-user collision`() {
        val wrongHash = BlindIndexUtil.hash("nonexistent_$ts@goldpet.com")!!
        val result = userRepository.findByEmailHash(wrongHash)
        assertTrue(result.isEmpty, "Wrong hash must not match any user")
    }

    @Test
    fun `rotation audit log is written — adminAuditService log called with rotation action`() {
        // admin_audit_logs.action is VARCHAR(20). Service uses "KEY_ROTATE" (10 chars) to
        // stay under the column limit while matching the repo convention of short verbs
        // (CREATE/UPDATE/DELETE/VIEW). Details payload carries the counts.
        piiKeyRotationService.rotate(dryRun = false, principalId = 999L)

        verify(adminAuditService).log(
            adminUserId = eq(999L),
            action = eq("KEY_ROTATE"),
            targetType = any(),
            targetId = isNull(),
            ipAddress = isNull(),
            requestPath = isNull(),
            requestMethod = isNull(),
            responseStatus = isNull(),
            details = any(),
            userAgent = isNull()
        )
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
