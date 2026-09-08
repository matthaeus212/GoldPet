package com.goldpet.domain.admin.service

import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.config.crypto.EncryptionConfig
import com.goldpet.config.crypto.EncryptionConverter
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Connection
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.sql.DataSource

@Service
class PiiKeyRotationService(
    private val jdbcTemplate: JdbcTemplate,
    private val transactionManager: PlatformTransactionManager,
    private val adminAuditService: AdminAuditService,
    private val dataSource: DataSource
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val converter = EncryptionConverter()

    data class VerificationResult(
        val expected: Int,
        val matched: Int,
        val mismatches: List<Long>
    )

    data class RotationResult(
        val rotated: Int,
        val skippedAlreadyNew: Int,
        val failed: Int,
        val mismatches: List<Long>,
        val verificationPass: VerificationResult,
        val dryRun: Boolean
    )

    class ConcurrentRotationException(msg: String) : RuntimeException(msg)

    private enum class RowOutcome { ROTATED, SKIPPED_ALREADY_NEW, FAILED }

    companion object {
        // arbitrary stable constant; prevents concurrent rotations
        private const val ADVISORY_LOCK_KEY: Long = 7734829104571003201L
        private val PII_COLUMNS = listOf("email", "name", "phone_number", "birth_date")
    }

    @Transactional(propagation = Propagation.NEVER)
    fun rotate(dryRun: Boolean, principalId: Long): RotationResult {
        // Postgres advisory locks are session-scoped: acquire and release MUST hit the same
        // connection. We hold one dedicated DataSource connection for the rotation lifetime
        // so the lock state is guaranteed consistent regardless of pool borrowing.
        dataSource.connection.use { lockConn ->
            val locked = tryAcquireAdvisoryLock(lockConn)
            if (!locked) {
                throw ConcurrentRotationException("Another rotation is in progress (advisory lock busy)")
            }
            try {
            val primary = EncryptionConfig.instance.primaryKey
            val secondary = EncryptionConfig.instance.secondaryKey

            val rows = jdbcTemplate.queryForList(
                "SELECT id, email, name, phone_number, birth_date FROM users WHERE is_active = true"
            )

            var rotated = 0
            var skippedAlreadyNew = 0
            var failed = 0
            val mismatches = mutableListOf<Long>()

            for (row in rows) {
                val userId = row["id"] as Long
                try {
                    val perRowTx = TransactionTemplate(transactionManager).apply {
                        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
                    }
                    val outcome = perRowTx.execute {
                        jdbcTemplate.queryForList(
                            "SELECT id FROM users WHERE id = ? FOR UPDATE", userId
                        )
                        rotateRow(userId, row, primary, secondary, dryRun)
                    } ?: RowOutcome.FAILED

                    when (outcome) {
                        RowOutcome.ROTATED -> rotated++
                        RowOutcome.SKIPPED_ALREADY_NEW -> skippedAlreadyNew++
                        RowOutcome.FAILED -> {
                            failed++
                            mismatches.add(userId)
                        }
                    }
                } catch (e: Exception) {
                    log.error("Rotation failed for user {}: {}", userId, e.message, e)
                    failed++
                    mismatches.add(userId)
                }
            }

            // admin_users 테이블도 동일하게 로테이션 (email + name 2개 PII 컬럼 + email_hash)
            val adminRows = jdbcTemplate.queryForList(
                "SELECT id, email, name FROM admin_users WHERE is_active = true"
            )
            for (row in adminRows) {
                val adminId = (row["id"] as Number).toLong()
                try {
                    val perRowTx = TransactionTemplate(transactionManager).apply {
                        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
                    }
                    val outcome = perRowTx.execute {
                        jdbcTemplate.queryForList(
                            "SELECT id FROM admin_users WHERE id = ? FOR UPDATE", adminId
                        )
                        rotateAdminRow(adminId, row, primary, secondary, dryRun)
                    } ?: RowOutcome.FAILED
                    when (outcome) {
                        RowOutcome.ROTATED -> rotated++
                        RowOutcome.SKIPPED_ALREADY_NEW -> skippedAlreadyNew++
                        RowOutcome.FAILED -> { failed++; mismatches.add(-adminId) }
                    }
                } catch (e: Exception) {
                    log.error("Rotation failed for admin_user {}: {}", adminId, e.message, e)
                    failed++
                    mismatches.add(-adminId)
                }
            }

            val verification = runVerificationPass(primary)

            adminAuditService.log(
                adminUserId = principalId,
                action = if (dryRun) "KEY_ROTATE_DRYRUN" else "KEY_ROTATE",
                targetType = "users+admin",
                details = "rotated=$rotated skipped=$skippedAlreadyNew failed=$failed users=${rows.size} admins=${adminRows.size} verificationMismatches=${verification.mismatches.size}"
            )

            return RotationResult(
                rotated = rotated,
                skippedAlreadyNew = skippedAlreadyNew,
                failed = failed,
                mismatches = mismatches,
                verificationPass = verification,
                dryRun = dryRun
            )
            } finally {
                releaseAdvisoryLock(lockConn)
            }
        }
    }

    private fun tryAcquireAdvisoryLock(conn: Connection): Boolean {
        conn.prepareStatement("SELECT pg_try_advisory_lock(?)").use { stmt ->
            stmt.setLong(1, ADVISORY_LOCK_KEY)
            stmt.executeQuery().use { rs ->
                return rs.next() && rs.getBoolean(1)
            }
        }
    }

    private fun releaseAdvisoryLock(conn: Connection) {
        try {
            conn.prepareStatement("SELECT pg_advisory_unlock(?)").use { stmt ->
                stmt.setLong(1, ADVISORY_LOCK_KEY)
                stmt.executeQuery().close()
            }
        } catch (e: Exception) {
            log.warn("Failed to release advisory lock: {}", e.message)
        }
    }

    private fun rotateRow(
        userId: Long,
        row: Map<String, Any?>,
        primary: String,
        secondary: String,
        dryRun: Boolean
    ): RowOutcome {
        val plaintexts = mutableMapOf<String, String?>()
        var anyNeedsRotation = false
        var anyFailed = false

        for (column in PII_COLUMNS) {
            val rawValue = row[column] as? String
            plaintexts[column] = null
            if (rawValue == null) continue

            val plaintextPrimary = tryDecrypt(rawValue, primary)
            if (plaintextPrimary != null) {
                plaintexts[column] = plaintextPrimary
                continue
            }

            if (secondary.isNotBlank()) {
                val plaintextSecondary = tryDecrypt(rawValue, secondary)
                if (plaintextSecondary != null) {
                    plaintexts[column] = plaintextSecondary
                    anyNeedsRotation = true
                    continue
                }
            }

            // Neither key worked. Ciphertext-shaped values must NOT be re-encrypted as plaintext
            // (prior bug double-encrypted rows when sibling column triggered UPDATE). Fail the row.
            if (looksLikeCiphertext(rawValue)) {
                log.error("Row {} column '{}' undecryptable by primary+secondary — marking FAILED to prevent re-encryption corruption", userId, column)
                anyFailed = true
            } else {
                plaintexts[column] = rawValue // legacy plaintext migration bootstrap
            }
        }

        if (anyFailed) {
            return RowOutcome.FAILED
        }

        if (!anyNeedsRotation) {
            return RowOutcome.SKIPPED_ALREADY_NEW
        }

        if (dryRun) {
            return RowOutcome.ROTATED
        }

        val newEmailCipher = plaintexts["email"]?.let { converter.convertToDatabaseColumn(it) }
        val newNameCipher = plaintexts["name"]?.let { converter.convertToDatabaseColumn(it) }
        val newPhoneCipher = plaintexts["phone_number"]?.let { converter.convertToDatabaseColumn(it) }
        val newBirthCipher = plaintexts["birth_date"]?.let { converter.convertToDatabaseColumn(it) }
        val newEmailHash = plaintexts["email"]?.let { BlindIndexUtil.hash(it) }

        jdbcTemplate.update(
            "UPDATE users SET email = ?, name = ?, phone_number = ?, birth_date = ?, email_hash = ? WHERE id = ?",
            newEmailCipher, newNameCipher, newPhoneCipher, newBirthCipher, newEmailHash, userId
        )
        return RowOutcome.ROTATED
    }

    /**
     * admin_users용 per-row 로테이션. email/name만 암호화 대상, email_hash 동시 갱신.
     * Bootstrap 케이스(plaintext 이메일 + NULL email_hash) 또는 Secondary→Primary 마이그레이션을
     * 모두 커버한다. 이미 Primary이고 email_hash가 정확하면 SKIPPED_ALREADY_NEW.
     */
    private fun rotateAdminRow(
        adminId: Long,
        row: Map<String, Any?>,
        primary: String,
        secondary: String,
        dryRun: Boolean
    ): RowOutcome {
        val rawEmail = row["email"] as? String ?: return RowOutcome.SKIPPED_ALREADY_NEW
        val rawName = row["name"] as? String ?: return RowOutcome.SKIPPED_ALREADY_NEW

        var needsRotation = false

        val emailPlain: String = run {
            val p = tryDecrypt(rawEmail, primary)
            if (p != null) return@run p
            needsRotation = true
            if (secondary.isNotBlank()) {
                val s = tryDecrypt(rawEmail, secondary)
                if (s != null) return@run s
            }
            if (looksLikeCiphertext(rawEmail)) {
                log.error("admin_user {} email undecryptable by primary+secondary — FAILED to prevent re-encryption corruption", adminId)
                return RowOutcome.FAILED
            }
            rawEmail // plaintext bootstrap
        }

        val namePlain: String = run {
            val p = tryDecrypt(rawName, primary)
            if (p != null) return@run p
            needsRotation = true
            if (secondary.isNotBlank()) {
                val s = tryDecrypt(rawName, secondary)
                if (s != null) return@run s
            }
            if (looksLikeCiphertext(rawName)) {
                log.error("admin_user {} name undecryptable by primary+secondary — FAILED to prevent re-encryption corruption", adminId)
                return RowOutcome.FAILED
            }
            rawName
        }

        // email_hash가 누락되거나 어긋나 있어도 갱신 필요 (bootstrap 직후 케이스)
        val currentHash = try {
            jdbcTemplate.queryForObject(
                "SELECT email_hash FROM admin_users WHERE id = ?", String::class.java, adminId
            )
        } catch (e: Exception) { null }
        val expectedHash = BlindIndexUtil.hash(emailPlain)
        if (currentHash != expectedHash) needsRotation = true

        if (!needsRotation) return RowOutcome.SKIPPED_ALREADY_NEW
        if (dryRun) return RowOutcome.ROTATED

        jdbcTemplate.update(
            "UPDATE admin_users SET email = ?, name = ?, email_hash = ? WHERE id = ?",
            converter.convertToDatabaseColumn(emailPlain),
            converter.convertToDatabaseColumn(namePlain),
            expectedHash,
            adminId
        )
        return RowOutcome.ROTATED
    }

    private fun runVerificationPass(primary: String): VerificationResult {
        val rows = jdbcTemplate.queryForList(
            "SELECT id, email, email_hash FROM users WHERE is_active = true"
        )
        var matched = 0
        val mismatches = mutableListOf<Long>()
        for (row in rows) {
            val userId = row["id"] as Long
            val emailCipher = row["email"] as? String
            val storedHash = row["email_hash"] as? String
            if (emailCipher == null || storedHash == null) {
                continue
            }
            val plaintext = tryDecrypt(emailCipher, primary)
            if (plaintext == null) {
                mismatches.add(userId)
                continue
            }
            val expectedHash = BlindIndexUtil.hash(plaintext)
            if (expectedHash == storedHash) {
                matched++
            } else {
                mismatches.add(userId)
            }
        }
        return VerificationResult(expected = rows.size, matched = matched, mismatches = mismatches)
    }

    private fun looksLikeCiphertext(s: String): Boolean {
        if (s.length < 40) return false
        return s.all { c -> c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '=' }
    }

    private fun tryDecrypt(dbData: String, keyString: String): String? {
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
}
