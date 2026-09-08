package com.goldpet.config.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DualKeyEncryptionConverterTest {

    companion object {
        private const val PRIMARY_KEY = "primary-key-for-unit-test-only!"
        private const val SECONDARY_KEY = "secondary-key-for-unit-tests!!!!"
        private const val OTHER_KEY = "other-key-not-in-config-for-test!"
    }

    private val converter = EncryptionConverter()

    private fun setConfig(primary: String, secondary: String = "") {
        EncryptionConfig(primary, secondary).validate()
    }

    /** Encrypt plaintext with the given key, then restore the config to primary+secondary. */
    private fun encryptWith(plaintext: String, key: String): String {
        setConfig(key, "")
        val ciphertext = converter.convertToDatabaseColumn(plaintext)!!
        setConfig(PRIMARY_KEY, SECONDARY_KEY)
        return ciphertext
    }

    @BeforeEach
    fun setUp() {
        setConfig(PRIMARY_KEY, SECONDARY_KEY)
    }

    @Test
    fun `convertToDatabaseColumn returns null for null input`() {
        assertNull(converter.convertToDatabaseColumn(null))
    }

    @Test
    fun `convertToDatabaseColumn encrypts using primary key`() {
        val ciphertext = converter.convertToDatabaseColumn("hello@example.com")
        assertNotNull(ciphertext)
        // Round-trip: must be decryptable with primary alone
        setConfig(PRIMARY_KEY, "")
        assertEquals("hello@example.com", converter.convertToEntityAttribute(ciphertext))
    }

    @Test
    fun `convertToEntityAttribute returns null for null input`() {
        assertNull(converter.convertToEntityAttribute(null))
    }

    @Test
    fun `convertToEntityAttribute decrypts ciphertext produced by primary key`() {
        val ciphertext = encryptWith("primary-user@example.com", PRIMARY_KEY)
        assertEquals("primary-user@example.com", converter.convertToEntityAttribute(ciphertext))
    }

    @Test
    fun `convertToEntityAttribute decrypts secondary-key ciphertext via fallback`() {
        // Simulates a row that was encrypted before the key rotation
        val ciphertext = encryptWith("old-user@example.com", SECONDARY_KEY)
        // Config has primary=PRIMARY, secondary=SECONDARY → fallback should succeed
        assertEquals("old-user@example.com", converter.convertToEntityAttribute(ciphertext))
    }

    @Test
    fun `convertToEntityAttribute returns null for ciphertext that neither key can decrypt`() {
        // Ciphertext from a third key not in config → both keys fail, string looks like ciphertext
        val ciphertext = encryptWith("user@example.com", OTHER_KEY)
        // looksLikeCiphertext(ciphertext) == true → returns null to prevent leakage
        assertNull(converter.convertToEntityAttribute(ciphertext))
    }

    @Test
    fun `convertToEntityAttribute returns plaintext for short or non-ciphertext data (backward compat)`() {
        // A legacy plaintext value (< 40 chars, not Base64 AES-GCM) passes through as-is
        setConfig(PRIMARY_KEY, SECONDARY_KEY)
        assertEquals("plain_email", converter.convertToEntityAttribute("plain_email"))
    }

    @Test
    fun `sanity - secondary unset behaves identically to a single-key converter`() {
        // When secondary is blank, dual-key is a no-op over single-key: same encrypt/decrypt
        setConfig(PRIMARY_KEY, "")
        val ciphertext = converter.convertToDatabaseColumn("sanity@example.com")!!
        assertEquals("sanity@example.com", converter.convertToEntityAttribute(ciphertext))
    }
}
