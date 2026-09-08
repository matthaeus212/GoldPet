package com.goldpet.domain.aiprofile.service

import com.goldpet.domain.aiprofile.entity.AIProfileRequest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

@Component
@Profile("local", "dev", "prod")
class LeonardoAIGenerationClient(
    private val eventPublisher: ApplicationEventPublisher
) : AIGenerationClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun requestGeneration(aiRequest: AIProfileRequest) {
        log.info("Publishing AIGenerationEvent for request id={}", aiRequest.id)
        eventPublisher.publishEvent(AIGenerationEvent(aiRequest.id))
    }
}
