package com.goldpet.config.crypto

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct

@Configuration
class EncryptionConfig(
    @Value("\${app.encryption.key-primary:\${app.encryption.key:MUST_BE_SET_VIA_ENV_VAR_DO_NOT_COMMIT}}")
    val primaryKey: String,
    @Value("\${app.encryption.key-secondary:}")
    val secondaryKey: String
) {
    val encryptionKey: String get() = primaryKey

    companion object {
        const val SENTINEL_MARKER = "MUST_BE_SET_VIA_ENV_VAR_DO_NOT_COMMIT"

        lateinit var instance: EncryptionConfig
            private set
    }

    @PostConstruct
    fun validate() {
        require(!primaryKey.contains(SENTINEL_MARKER)) {
            "ENCRYPTION_KEY env var is not set — refusing to start with YAML sentinel primaryKey"
        }
        if (secondaryKey.isNotBlank()) {
            require(!secondaryKey.contains(SENTINEL_MARKER)) {
                "ENCRYPTION_KEY env var is not set — refusing to start with YAML sentinel secondaryKey"
            }
        }
        instance = this
    }
}
