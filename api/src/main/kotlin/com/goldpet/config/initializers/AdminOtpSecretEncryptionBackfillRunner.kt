// 기존 평문 admin otp_secret 을 AES-GCM 으로 암호화 재저장하는 원타임(멱등) 러너
package com.goldpet.config.initializers

import com.goldpet.config.crypto.EncryptionConverter
import com.goldpet.domain.admin.repository.AdminUserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * EXT-CDX-005 — admin_users.otp_secret 이 과거 평문으로 저장된 행을 AES-GCM 으로 암호화 재저장한다.
 *
 * ## 멱등성
 * - EncryptionConverter.convertToEntityAttribute(raw) 가 raw 를 그대로 반환하면(=복호화 불가한 평문)
 *   암호화가 필요한 행으로 판정한다. 이미 암호문이면 복호화 결과가 raw 와 달라(또는 null) skip.
 * - 신규 저장 경로(confirm2fa 등)는 converter 가 write 시 항상 암호화하므로 새 평문은 생기지 않는다.
 *
 * codegen 프로파일(DB 없이 스펙 생성)에서는 실행하지 않는다.
 */
@Configuration
@Profile("!codegen")
class AdminOtpSecretEncryptionBackfillRunner(
    private val adminUserRepository: AdminUserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun backfillAdminOtpSecretEncryption(): CommandLineRunner = CommandLineRunner {
        val converter = EncryptionConverter()
        var migrated = 0
        var skipped = 0
        var failed = 0
        val rows = try {
            adminUserRepository.findRawOtpSecrets()
        } catch (ex: Exception) {
            log.warn("otp_secret 마이그레이션 조회 실패(스킵): {}", ex.message)
            return@CommandLineRunner
        }
        for (row in rows) {
            val id = (row[0] as Number).toLong()
            val raw = row[1] as? String ?: continue
            try {
                // 암호문이면 복호화가 성공해 raw 와 다른 값(또는 null)을 반환 → skip.
                // 평문이면 복호화 fallback 으로 raw 를 그대로 반환 → 암호화 필요.
                val decoded = converter.convertToEntityAttribute(raw)
                if (decoded == raw) {
                    val encrypted = converter.convertToDatabaseColumn(raw)
                    if (encrypted != null) {
                        adminUserRepository.updateRawOtpSecret(id, encrypted)
                        migrated++
                    } else {
                        skipped++
                    }
                } else {
                    skipped++
                }
            } catch (ex: Exception) {
                failed++
                log.warn("otp_secret 암호화 재저장 실패 adminId={}: {}", id, ex.message)
            }
        }
        if (migrated > 0 || failed > 0) {
            log.info("Admin otp_secret 암호화 마이그레이션: migrated={}, skipped={}, failed={} (total {})",
                migrated, skipped, failed, rows.size)
        }
    }
}
