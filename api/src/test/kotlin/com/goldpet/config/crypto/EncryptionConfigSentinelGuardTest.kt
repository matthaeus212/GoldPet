package com.goldpet.config.crypto

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EncryptionConfigSentinelGuardTest {

    @Test
    fun `validate rejects primary key that IS the sentinel`() {
        val config = EncryptionConfig(
            primaryKey = EncryptionConfig.SENTINEL_MARKER,
            secondaryKey = ""
        )
        assertThrows<IllegalArgumentException> { config.validate() }
    }

    @Test
    fun `validate rejects primary key containing sentinel as substring`() {
        val config = EncryptionConfig(
            primaryKey = "prefix-${EncryptionConfig.SENTINEL_MARKER}-suffix",
            secondaryKey = ""
        )
        assertThrows<IllegalArgumentException> { config.validate() }
    }

    @Test
    fun `validate rejects non-blank secondary key containing sentinel`() {
        val config = EncryptionConfig(
            primaryKey = "valid-primary-key",
            secondaryKey = EncryptionConfig.SENTINEL_MARKER
        )
        assertThrows<IllegalArgumentException> { config.validate() }
    }

    @Test
    fun `validate passes with valid primary and blank secondary`() {
        val config = EncryptionConfig(
            primaryKey = "valid-primary-key",
            secondaryKey = ""
        )
        config.validate()
    }

    @Test
    fun `validate passes with valid primary and valid secondary`() {
        val config = EncryptionConfig(
            primaryKey = "valid-primary-key",
            secondaryKey = "valid-secondary-key"
        )
        config.validate()
    }

    @Test
    fun `validate sets the companion instance on success`() {
        val config = EncryptionConfig(
            primaryKey = "valid-primary-key",
            secondaryKey = ""
        )
        config.validate()
        assertSame(config, EncryptionConfig.instance)
    }

    @Test
    fun `validate skips sentinel check for whitespace-only secondary (isNotBlank guard)`() {
        // "   ".isNotBlank() == false in Kotlin, so the sentinel check is skipped
        val config = EncryptionConfig(
            primaryKey = "valid-primary-key",
            secondaryKey = "   "
        )
        config.validate()
    }
}
