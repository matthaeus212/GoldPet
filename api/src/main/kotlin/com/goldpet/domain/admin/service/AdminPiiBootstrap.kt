package com.goldpet.domain.admin.service

import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.config.crypto.EncryptionConfig
import com.goldpet.config.crypto.EncryptionConverter
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * admin_users 테이블의 email/name이 V55 마이그레이션으로 암호화 대상이 된 직후 한 번,
 * 그리고 후속 배포 때 email_hash가 누락된 행이 있으면 자동으로 채운다.
 *
 * 로그인은 `findByEmailHash`만 사용하므로 email_hash가 NULL이면 기존 관리자가 전부 로그인
 * 불가. 수동 SQL 대신 앱 시작 시 자동 복구되도록 한다. 이미 암호화+해시가 올바르면 no-op.
 */
@Component
@Profile("!codegen")
class AdminPiiBootstrap(
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val converter = EncryptionConverter()

    @EventListener(ApplicationReadyEvent::class)
    fun bootstrap() {
        val missing = jdbcTemplate.queryForList(
            "SELECT id, email, name FROM admin_users WHERE email_hash IS NULL AND is_active = true"
        )
        if (missing.isEmpty()) {
            log.info("AdminPiiBootstrap: 모든 admin_users에 email_hash 존재. skip.")
            return
        }
        val primary = EncryptionConfig.instance.primaryKey
        val secondary = EncryptionConfig.instance.secondaryKey
        log.info("AdminPiiBootstrap: email_hash 누락 {}건 복구 시작", missing.size)
        for (row in missing) {
            val id = (row["id"] as Number).toLong()
            val rawEmail = row["email"] as? String ?: continue
            val rawName = row["name"] as? String ?: continue
            try {
                val emailPlain = decryptOrPassthrough(rawEmail, primary, secondary)
                val namePlain = decryptOrPassthrough(rawName, primary, secondary)
                jdbcTemplate.update(
                    "UPDATE admin_users SET email = ?, name = ?, email_hash = ? WHERE id = ?",
                    converter.convertToDatabaseColumn(emailPlain),
                    converter.convertToDatabaseColumn(namePlain),
                    BlindIndexUtil.hash(emailPlain),
                    id
                )
                log.info("AdminPiiBootstrap: id={} 복구 완료", id)
            } catch (e: Exception) {
                log.error("AdminPiiBootstrap: id={} 복구 실패: {}", id, e.message, e)
            }
        }
    }

    private fun decryptOrPassthrough(raw: String, primary: String, secondary: String): String {
        tryDecrypt(raw, primary)?.let { return it }
        if (secondary.isNotBlank()) tryDecrypt(raw, secondary)?.let { return it }
        return raw // 평문 fallback (마이그레이션 전 plaintext)
    }

    private fun tryDecrypt(dbData: String, keyString: String): String? {
        return try {
            val decoded = Base64.getDecoder().decode(dbData)
            if (decoded.size < 29) return null
            val iv = decoded.sliceArray(0 until 12)
            val encrypted = decoded.sliceArray(12 until decoded.size)
            val key = SecretKeySpec(keyString.toByteArray(Charsets.UTF_8).copyOf(32), "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
