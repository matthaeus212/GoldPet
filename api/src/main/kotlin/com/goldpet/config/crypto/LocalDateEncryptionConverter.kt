package com.goldpet.config.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

@Converter
class LocalDateEncryptionConverter : AttributeConverter<LocalDate?, String?> {

    private val log = LoggerFactory.getLogger(LocalDateEncryptionConverter::class.java)

    private val algorithm = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivLength = 12
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd

    override fun convertToDatabaseColumn(attribute: LocalDate?): String? {
        if (attribute == null) return null
        try {
            val dateString = attribute.format(dateFormatter)
            val key = getKey(EncryptionConfig.instance.primaryKey)
            val iv = ByteArray(ivLength)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
            val encrypted = cipher.doFinal(dateString.toByteArray(Charsets.UTF_8))

            val combined = iv + encrypted
            return Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            throw RuntimeException("LocalDate encryption failed", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): LocalDate? {
        if (dbData == null) return null
        try {
            return decryptWith(dbData, EncryptionConfig.instance.primaryKey)
        } catch (primaryEx: Exception) {
            val secondary = EncryptionConfig.instance.secondaryKey
            if (secondary.isNotBlank()) {
                try {
                    return decryptWith(dbData, secondary)
                } catch (secondaryEx: Exception) {
                    return plaintextFallback(dbData, secondaryEx)
                }
            }
            return plaintextFallback(dbData, primaryEx)
        }
    }

    private fun decryptWith(dbData: String, keyString: String): LocalDate {
        val key = getKey(keyString)
        val combined = Base64.getDecoder().decode(dbData)

        val iv = combined.sliceArray(0 until ivLength)
        val encrypted = combined.sliceArray(ivLength until combined.size)

        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
        val dateString = String(cipher.doFinal(encrypted), Charsets.UTF_8)
        return LocalDate.parse(dateString, dateFormatter)
    }

    private fun plaintextFallback(dbData: String, cause: Exception): LocalDate? {
        return try {
            log.warn("Decryption failed for LocalDate column — trying plaintext fallback. Cause: {}", cause.message)
            LocalDate.parse(dbData)
        } catch (parseEx: Exception) {
            log.error("Failed to parse LocalDate value as both encrypted and plaintext: {}", parseEx.message)
            null
        }
    }

    private fun getKey(keyString: String): SecretKeySpec {
        val keyBytes = keyString.toByteArray(Charsets.UTF_8).copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }
}
