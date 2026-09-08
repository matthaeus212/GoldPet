package com.goldpet.config.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LocalDateDualKeyConverterTest {

    companion object {
        private const val PRIMARY_KEY = "primary-key-for-unit-test-only!"
        private const val SECONDARY_KEY = "secondary-key-for-unit-tests!!!!"
        private const val OTHER_KEY = "other-key-not-in-config-for-test!"
    }

    private val converter = LocalDateEncryptionConverter()

    private fun setConfig(primary: String, secondary: String = "") {
        EncryptionConfig(primary, secondary).validate()
    }

    /** Encrypt date with the given key, then restore the config to primary+secondary. */
    private fun encryptWith(date: LocalDate, key: String): String {
        setConfig(key, "")
        val ciphertext = converter.convertToDatabaseColumn(date)!!
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
    fun `convertToDatabaseColumn encrypts LocalDate using primary key`() {
        val date = LocalDate.of(1990, 5, 15)
        val ciphertext = converter.convertToDatabaseColumn(date)
        assertNotNull(ciphertext)
        // Round-trip: must be decryptable with primary alone
        setConfig(PRIMARY_KEY, "")
        assertEquals(date, converter.convertToEntityAttribute(ciphertext))
    }

    @Test
    fun `convertToEntityAttribute returns null for null input`() {
        assertNull(converter.convertToEntityAttribute(null))
    }

    @Test
    fun `convertToEntityAttribute decrypts ciphertext produced by primary key`() {
        val date = LocalDate.of(2000, 1, 1)
        val ciphertext = encryptWith(date, PRIMARY_KEY)
        assertEquals(date, converter.convertToEntityAttribute(ciphertext))
    }

    @Test
    fun `convertToEntityAttribute decrypts secondary-key ciphertext via fallback`() {
        val date = LocalDate.of(1995, 12, 31)
        val ciphertext = encryptWith(date, SECONDARY_KEY)
        // Config has primary=PRIMARY, secondary=SECONDARY → fallback should succeed
        assertEquals(date, converter.convertToEntityAttribute(ciphertext))
    }

    @Test
    fun `convertToEntityAttribute returns null for ciphertext that neither key can decrypt`() {
        val date = LocalDate.of(1988, 3, 22)
        val ciphertext = encryptWith(date, OTHER_KEY)
        // Both keys fail; Base64 ciphertext is not parseable as LocalDate → null
        assertNull(converter.convertToEntityAttribute(ciphertext))
    }

    @Test
    fun `convertToEntityAttribute uses plaintext LocalDate fallback for ISO-date string`() {
        // Legacy rows stored as "yyyy-MM-dd" plaintext (pre-encryption migration)
        // "2000-01-01" contains '-' which is not a Base64 char → decryption throws → fallback
        setConfig(PRIMARY_KEY, SECONDARY_KEY)
        assertEquals(LocalDate.of(2000, 1, 1), converter.convertToEntityAttribute("2000-01-01"))
    }

    @Test
    fun `sanity - secondary unset behaves identically to a single-key converter`() {
        val date = LocalDate.of(1992, 6, 10)
        setConfig(PRIMARY_KEY, "")
        val ciphertext = converter.convertToDatabaseColumn(date)!!
        assertEquals(date, converter.convertToEntityAttribute(ciphertext))
    }
}
