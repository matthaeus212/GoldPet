package com.goldpet.domain.admin.service

import com.goldpet.config.crypto.EncryptionConverter
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PiiEncryptionMigrationService(
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val converter = EncryptionConverter()

    data class MigrationResult(
        val totalUsers: Int,
        val encryptedCount: Int,
        val alreadyEncryptedCount: Int,
        val nullSkippedCount: Int,
        val errorCount: Int,
        val errors: List<String>
    )

    /**
     * 기존 평문 PII 데이터를 AES-256-GCM으로 암호화합니다.
     * - email, name, phone_number 컬럼 대상
     * - 이미 암호화된 값은 건너뜁니다 (Base64 + 복호화 성공 여부로 판별)
     * - 트랜잭션 내에서 실행되며, 오류 발생 시 개별 사용자 단위로 skip
     */
    @Transactional
    fun migrateExistingPiiData(): MigrationResult {
        val columns = listOf("email", "name", "phone_number", "birth_date")
        val errors = mutableListOf<String>()
        var encryptedCount = 0
        var alreadyEncryptedCount = 0
        var nullSkippedCount = 0
        var errorCount = 0

        val rows = jdbcTemplate.queryForList(
            "SELECT id, email, name, phone_number, birth_date FROM users WHERE is_active = true"
        )
        val totalUsers = rows.size

        log.info("PII encryption migration started — {} active users to process", totalUsers)

        for (row in rows) {
            val userId = row["id"] as Long

            for (column in columns) {
                val rawValue = row[column] as? String
                if (rawValue == null) {
                    nullSkippedCount++
                    continue
                }

                if (isAlreadyEncrypted(rawValue)) {
                    alreadyEncryptedCount++
                    continue
                }

                try {
                    val encrypted = converter.convertToDatabaseColumn(rawValue)
                    jdbcTemplate.update(
                        "UPDATE users SET $column = ? WHERE id = ?",
                        encrypted, userId
                    )
                    encryptedCount++
                } catch (e: Exception) {
                    errorCount++
                    val msg = "Failed to encrypt $column for user $userId: ${e.message}"
                    errors.add(msg)
                    log.error(msg, e)
                }
            }
        }

        log.info(
            "PII encryption migration completed — encrypted: {}, already encrypted: {}, null skipped: {}, errors: {}",
            encryptedCount, alreadyEncryptedCount, nullSkippedCount, errorCount
        )

        return MigrationResult(
            totalUsers = totalUsers,
            encryptedCount = encryptedCount,
            alreadyEncryptedCount = alreadyEncryptedCount,
            nullSkippedCount = nullSkippedCount,
            errorCount = errorCount,
            errors = errors
        )
    }

    /**
     * 값이 이미 암호화되어 있는지 확인합니다.
     * Base64 디코딩 + AES-GCM 복호화를 직접 시도하여 판별합니다.
     * (EncryptionConverter의 fallback을 우회하여 정확한 판별)
     */
    private fun isAlreadyEncrypted(value: String): Boolean {
        return try {
            val decoded = java.util.Base64.getDecoder().decode(value)
            // 최소 길이: IV(12) + GCM tag(16) + 1 byte data = 29
            if (decoded.size < 29) return false

            val iv = decoded.sliceArray(0 until 12)
            val encrypted = decoded.sliceArray(12 until decoded.size)

            val keyString = com.goldpet.config.crypto.EncryptionConfig.instance.encryptionKey
            val keyBytes = keyString.toByteArray(Charsets.UTF_8).copyOf(32)
            val key = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")

            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted)
            true
        } catch (e: Exception) {
            false
        }
    }
}
