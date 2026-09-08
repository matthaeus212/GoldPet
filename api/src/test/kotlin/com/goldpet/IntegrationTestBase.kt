package com.goldpet

import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.config.crypto.EncryptionConfig
import com.goldpet.config.crypto.EncryptionConverter
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.sql.DataSource

/**
 * 통합 테스트 베이스.
 *
 * `deploy-local/docker-compose.yml`로 띄운 로컬 PostGIS 컨테이너(localhost:5433)를 사용한다.
 * 테스트 실행 전에 `./deploy-local/scripts/start.sh`로 인프라를 기동해야 한다.
 *
 * 이전에는 `application-test.yml`이 원격 dev DB(`101.250.201.36`)를 직접 가리켜
 * `./gradlew test` 실행 시마다 실 DB에 row가 누출되는 격리 결함이 있었다.
 * 이제 datasource는 `application-test.yml`이 명시적으로 localhost:5433을 가리키므로
 * 컨테이너가 없으면 테스트가 명시적으로 connection refused로 실패한다.
 *
 * 추가 방어: `TEST_DB_URL` 환경변수로 오버라이드하더라도 host가 localhost/127.0.0.1이
 * 아니면 `verifyLocalDatasource()`가 즉시 실패시킨다. 2026-04-16에 원격 dev DB로
 * 테스트가 실행돼 test 키로 암호화된 row 6건이 누출된 사고의 재발 방지.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    @Autowired
    private lateinit var environment: Environment

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var encryptionConfigBean: EncryptionConfig

    /**
     * Restores `EncryptionConfig.instance` to the Spring-managed bean before each test.
     *
     * Unit tests (e.g. `PiiKeyRotationServiceTest`) construct their own `EncryptionConfig`
     * with unrelated key values and call `validate()`, which overwrites the static
     * `instance` field. A subsequent integration test running in the same JVM would
     * otherwise observe those polluted keys and fail to decrypt/encrypt correctly.
     * Calling `validate()` on the autowired bean re-pins `instance` to the yml-configured
     * test keys every time.
     */
    @BeforeEach
    fun restoreEncryptionConfigSingleton() {
        encryptionConfigBean.validate()
    }

    /**
     * Clears any PostgreSQL advisory locks that leaked from a previous test.
     *
     * PiiKeyRotationService calls pg_try_advisory_lock on one HikariCP connection and
     * pg_advisory_unlock on another (advisory locks are session-scoped; JdbcTemplate
     * without a wrapping transaction returns each connection to the pool immediately).
     * We exhaust the entire pool and call pg_advisory_unlock_all() on every session so
     * no stale lock survives into the next test.
     */
    @BeforeEach
    fun clearAdvisoryLocks() {
        val hikari = dataSource as HikariDataSource
        val conns = (1..hikari.maximumPoolSize).map { hikari.connection }
        try {
            conns.forEach { conn ->
                conn.createStatement().use { it.execute("SELECT pg_advisory_unlock_all()") }
            }
        } finally {
            conns.forEach { it.close() }
        }
    }

    /**
     * Fixes active users whose email cannot be decrypted by either the primary or secondary
     * test key — e.g., rows left over from previous dev sessions that used a different key.
     *
     * PiiKeyRotationService.rotateRow() uses a plaintext-passthrough for columns that
     * cannot be decrypted by either key: anyNeedsRotation stays false, so the row is
     * classified SKIPPED_ALREADY_NEW without re-encrypting.  runVerificationPass() then
     * fails to decrypt → mismatch → controller returns HTTP 500 instead of 200.
     *
     * Strategy: treat the raw DB value as the "plaintext" and re-encrypt it under the
     * current primary key so that decrypt→hash round-trip is consistent.
     * We permanently fix these rows so future test runs also benefit.
     */
    @BeforeEach
    fun migratePreExistingPlaintextEmails() {
        val primaryKey = EncryptionConfig.instance.primaryKey
        val secondaryKey = EncryptionConfig.instance.secondaryKey
        val converter = EncryptionConverter()
        jdbcTemplate.queryForList(
            "SELECT id, email FROM users WHERE is_active = true AND email IS NOT NULL"
        ).forEach { row ->
            val userId = row["id"] as Long
            val rawEmail = row["email"] as? String ?: return@forEach
            // Already decryptable with primary key — OK as-is
            if (tryDecryptAesGcm(rawEmail, primaryKey) != null) return@forEach
            // Secondary-key encrypted — rotation will handle it during the test
            if (secondaryKey.isNotBlank() && tryDecryptAesGcm(rawEmail, secondaryKey) != null) return@forEach
            // Orphan ciphertext from a previous unknown key. Re-encrypting it as "plaintext"
            // produces a double-encrypted blob that overflows VARCHAR(255), and leaving it
            // alone makes runVerificationPass mismatch on it (forces HTTP 500).
            // Deactivate — the rotation loop and verification both filter is_active = true.
            if (looksLikeBase64Ciphertext(rawEmail)) {
                jdbcTemplate.update("UPDATE users SET is_active = false WHERE id = ?", userId)
                return@forEach
            }
            // Genuine plaintext (e.g., "foo@bar.com") — encrypt under primary.
            jdbcTemplate.update(
                "UPDATE users SET email = ?, email_hash = ? WHERE id = ?",
                converter.convertToDatabaseColumn(rawEmail),
                BlindIndexUtil.hash(rawEmail),
                userId
            )
        }

        // Same cleanup for admin_users: orphan ciphertext rows (encrypted with an unknown key,
        // e.g. dev-server keys leaked into the local test DB) cause rotateAdminRow() to return
        // FAILED, which surfaces as result.failed > 0 and HTTP 500 in HttpLayerRotationIntegrationTest.
        // PiiKeyRotationService queries admin_users WHERE is_active = true, so deactivating
        // the orphan row removes it from the rotation scope without deleting real data.
        jdbcTemplate.queryForList(
            "SELECT id, email FROM admin_users WHERE is_active = true AND email IS NOT NULL"
        ).forEach { row ->
            val adminId = (row["id"] as Number).toLong()
            val rawEmail = row["email"] as? String ?: return@forEach
            if (tryDecryptAesGcm(rawEmail, primaryKey) != null) return@forEach
            if (secondaryKey.isNotBlank() && tryDecryptAesGcm(rawEmail, secondaryKey) != null) return@forEach
            if (looksLikeBase64Ciphertext(rawEmail)) {
                jdbcTemplate.update("UPDATE admin_users SET is_active = false WHERE id = ?", adminId)
            }
        }
    }

    private fun looksLikeBase64Ciphertext(s: String): Boolean {
        if (s.length < 40) return false
        return s.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }
    }

    private fun tryDecryptAesGcm(data: String, keyString: String): String? {
        try {
            val decoded = Base64.getDecoder().decode(data)
            if (decoded.size < 29) return null
            val iv = decoded.sliceArray(0 until 12)
            val encrypted = decoded.sliceArray(12 until decoded.size)
            val key = SecretKeySpec(keyString.toByteArray(Charsets.UTF_8).copyOf(32), "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
    }

    @BeforeEach
    fun verifyLocalDatasource() {
        val url = environment.getProperty("spring.datasource.url")
            ?: error("spring.datasource.url is not configured")
        val host = Regex("""jdbc:postgresql://([^:/]+)""").find(url)?.groupValues?.get(1)
            ?: error("Cannot parse datasource URL: $url")
        val allowedHosts = setOf("localhost", "127.0.0.1")
        check(host in allowedHosts) {
            "Integration tests refuse to run against non-local host '$host'. " +
                "Full URL: $url. Integration tests must target a local test database."
        }
    }

    // ------------------------------------------------------------------
    // T1-1a: Hibernate Statistics 기반 쿼리 카운트 헬퍼
    // ------------------------------------------------------------------
    // `application-test.yml` 의 `hibernate.generate_statistics=true` 가 활성화돼 있을 때
    // 동작한다. N+1 방지용 통합 테스트(특히 `findAllByUrlIn` 같은 배치 lookup) 에서
    // `withinStatementBudget(1) { ... }` 로 prepared statement 개수를 강제.
    //
    // 예:
    // ```
    // withinStatementBudget(max = 1) {
    //     val result = fileAttachmentRepository.findAllByUrlIn(urls)
    //     assertEquals(urls.size, result.size)
    // }
    // ```
    // ------------------------------------------------------------------

    @Autowired
    protected lateinit var entityManagerFactory: EntityManagerFactory

    protected val hibernateStatistics: Statistics
        get() = entityManagerFactory
            .unwrap(SessionFactory::class.java)
            .statistics

    /**
     * Executes [block] while measuring prepared statement count via Hibernate Statistics.
     *
     * @param max maximum allowed prepared statements during [block] execution (inclusive).
     * @throws IllegalStateException if actual count exceeds [max].
     */
    protected fun <T> withinStatementBudget(max: Long, block: () -> T): T {
        val stats = hibernateStatistics
        check(stats.isStatisticsEnabled) {
            "Hibernate statistics is disabled. Add `hibernate.generate_statistics=true` to application-test.yml."
        }
        val before = stats.prepareStatementCount
        val result = block()
        val actual = stats.prepareStatementCount - before
        check(actual <= max) {
            "Statement budget exceeded: expected ≤$max but observed $actual prepared statements. " +
                "Likely N+1 — inspect service layer for missing batch lookup."
        }
        return result
    }
}
