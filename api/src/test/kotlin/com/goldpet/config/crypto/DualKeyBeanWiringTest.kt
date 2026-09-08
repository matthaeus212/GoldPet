package com.goldpet.config.crypto

import com.goldpet.IntegrationTestBase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Verifies that the Spring context wires dual-key EncryptionConfig from application-test.yml
 * and that all crypto beans (EncryptionConverter, BlindIndexUtil) resolve without NPE.
 * Covers the DualKeyBeanWiringTest requirement from the plan §5 integration suite.
 *
 * Note: EncryptionConverter is annotated @Converter (JPA), not @Component, so it is not a
 * Spring bean and must be instantiated directly. It reads EncryptionConfig.instance at call
 * time, which is set during Spring context startup via @PostConstruct validate().
 */
@Tag("integration")
class DualKeyBeanWiringTest : IntegrationTestBase() {

    @Autowired private lateinit var encryptionConfig: EncryptionConfig

    // Instantiated directly — @Converter is JPA-only, not a Spring-managed bean.
    private val encryptionConverter = EncryptionConverter()

    @Test
    fun `EncryptionConfig instance is set and exposes both keys from application-test yml`() {
        val instance = EncryptionConfig.instance
        assertNotNull(instance)
        assertEquals("test-encryption-key-32-chars!!!", instance.primaryKey)
        assertEquals("test-secondary-key-32-chars!!!!", instance.secondaryKey)
        assertTrue(instance.secondaryKey.isNotBlank())
    }

    @Test
    fun `encryptionKey getter returns primary key for backward-compat callers`() {
        assertEquals(EncryptionConfig.instance.primaryKey, EncryptionConfig.instance.encryptionKey)
    }

    @Test
    fun `BlindIndexUtil hash resolves EncryptionConfig instance without NPE`() {
        val hash = BlindIndexUtil.hash("hashtest@example.com")
        assertNotNull(hash)
        assertTrue(hash!!.isNotBlank())
    }

    @Test
    fun `BlindIndexUtil hash is deterministic for same input`() {
        val h1 = BlindIndexUtil.hash("repeat@example.com")
        val h2 = BlindIndexUtil.hash("repeat@example.com")
        assertEquals(h1, h2)
    }

    @Test
    fun `EncryptionConverter round-trips plaintext via primary key`() {
        val plaintext = "wiring-test@example.com"
        val ciphertext = encryptionConverter.convertToDatabaseColumn(plaintext)
        assertNotNull(ciphertext)
        val decrypted = encryptionConverter.convertToEntityAttribute(ciphertext)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `EncryptionConverter returns null for null input`() {
        assertNull(encryptionConverter.convertToDatabaseColumn(null))
        assertNull(encryptionConverter.convertToEntityAttribute(null))
    }

    @Test
    fun `EncryptionConfig validate rejects sentinel in primaryKey`() {
        val bad = EncryptionConfig("MUST_BE_SET_VIA_ENV_VAR_DO_NOT_COMMIT", "")
        val ex = assertThrows(IllegalArgumentException::class.java) { bad.validate() }
        assertTrue(ex.message!!.contains("primaryKey"))
    }

    @Test
    fun `EncryptionConfig validate rejects sentinel in secondaryKey`() {
        val bad = EncryptionConfig("valid-primary-key-32-chars!!!!!!", "MUST_BE_SET_VIA_ENV_VAR_DO_NOT_COMMIT")
        val ex = assertThrows(IllegalArgumentException::class.java) { bad.validate() }
        assertTrue(ex.message!!.contains("secondaryKey"))
    }

    @Test
    fun `EncryptionConfig validate accepts blank secondaryKey (single-key mode)`() {
        // blank secondary == single-key mode; must not throw
        val cfg = EncryptionConfig("valid-primary-key-32-chars!!!!!!", "")
        assertDoesNotThrow { cfg.validate() }
    }
}
