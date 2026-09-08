package com.goldpet.domain.auth.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local", "test", "codegen")
class LoggingSmsSender : SmsSender {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(phoneNumber: String, message: String) {
        logger.info("[DEV SMS] To: $phoneNumber, Message: $message")
    }
}
