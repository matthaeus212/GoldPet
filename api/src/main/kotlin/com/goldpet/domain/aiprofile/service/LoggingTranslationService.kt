package com.goldpet.domain.aiprofile.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("test", "codegen")
class LoggingTranslationService : TranslationService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun translate(text: String, sourceLang: String, targetLang: String): String {
        log.info("LoggingTranslationService: translate('{}', {} -> {}) - returning original", text, sourceLang, targetLang)
        return text
    }
}
