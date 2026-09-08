package com.goldpet.config.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

@Converter
class EncryptionConverter : AttributeConverter<String?, String?> {

    private val log = LoggerFactory.getLogger(EncryptionConverter::class.java)

    private val algorithm = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivLength = 12

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        try {
            val key = getKey(EncryptionConfig.instance.primaryKey)
            val iv = ByteArray(ivLength)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance(algorithm)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
            val encrypted = cipher.doFinal(attribute.toByteArray(Charsets.UTF_8))

            // Prepend IV to encrypted data
            val combined = iv + encrypted
            return Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            throw RuntimeException("Encryption failed", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        try {
            return decryptWith(dbData, EncryptionConfig.instance.primaryKey)
        } catch (primaryEx: Exception) {
            val secondary = EncryptionConfig.instance.secondaryKey
            if (secondary.isNotBlank()) {
                try {
                    return decryptWith(dbData, secondary)
                } catch (secondaryEx: Exception) {
                    if (looksLikeCiphertext(dbData)) {
                        log.error("Decryption failed for ciphertext-shaped value with both keys (len={}) — returning null to prevent leakage. Primary cause: {}, secondary cause: {}", dbData.length, primaryEx.message, secondaryEx.message)
                        return null
                    }
                    log.warn("Decryption failed with both keys — treating as plaintext (migration fallback). Cause: {}", secondaryEx.message)
                    return dbData
                }
            }
            if (looksLikeCiphertext(dbData)) {
                log.error("Decryption failed for ciphertext-shaped value (len={}) — returning null to prevent leakage. Cause: {}", dbData.length, primaryEx.message)
                return null
            }
            log.warn("Decryption failed — treating as plaintext (migration fallback). Cause: {}", primaryEx.message)
            return dbData
        }
    }

    private fun decryptWith(dbData: String, keyString: String): String {
        val key = getKey(keyString)
        val combined = Base64.getDecoder().decode(dbData)

        val iv = combined.sliceArray(0 until ivLength)
        val encrypted = combined.sliceArray(ivLength until combined.size)

        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun looksLikeBase64Char(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '='

    private fun looksLikeCiphertext(s: String): Boolean {
        if (s.length < 40) return false
        return s.all { looksLikeBase64Char(it) }
    }

    private fun getKey(keyString: String): SecretKeySpec {
        // Ensure key is exactly 32 bytes for AES-256
        val keyBytes = keyString.toByteArray(Charsets.UTF_8).copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }
}
