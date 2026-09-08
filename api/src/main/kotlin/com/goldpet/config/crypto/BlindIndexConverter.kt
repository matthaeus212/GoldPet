package com.goldpet.config.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Converter
class BlindIndexConverter : AttributeConverter<String?, String?> {

    private val algorithm = "HmacSHA256"

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        try {
            val key = getKey()
            val mac = Mac.getInstance(algorithm)
            mac.init(key)
            val hash = mac.doFinal(attribute.toByteArray(StandardCharsets.UTF_8))
            return Base64.getEncoder().encodeToString(hash)
        } catch (e: Exception) {
            throw RuntimeException("Hashing failed", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        // Hashing is one-way, so we cannot decrypt it.
        // This method is only used when reading the hash from DB, which returns the hash itself.
        return dbData
    }

    private fun getKey(): SecretKeySpec {
        val keyString = EncryptionConfig.instance.encryptionKey
        // Use the same key as encryption, or derive a specific one.
        // For simplicity and consistency, we use the first 32 bytes of the configured key.
        val keyBytes = keyString.toByteArray(StandardCharsets.UTF_8).copyOf(32)
        return SecretKeySpec(keyBytes, algorithm)
    }
}
