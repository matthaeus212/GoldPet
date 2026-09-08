package com.goldpet.config.crypto

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Utility for computing blind index (HMAC-SHA256) hashes.
 * Use this instead of BlindIndexConverter to avoid double-hashing on JPA save.
 */
object BlindIndexUtil {

    private const val ALGORITHM = "HmacSHA256"

    fun hash(plaintext: String?): String? {
        if (plaintext == null) return null
        val keyString = EncryptionConfig.instance.encryptionKey
        val keyBytes = keyString.toByteArray(StandardCharsets.UTF_8).copyOf(32)
        val key = SecretKeySpec(keyBytes, ALGORITHM)
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key)
        val hashBytes = mac.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}
