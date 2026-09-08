package com.goldpet.domain.aiprofile.service

import com.goldpet.domain.aiprofile.entity.AIProfileRequest
import com.goldpet.domain.aiprofile.entity.AIRequestStatus
import com.goldpet.domain.aiprofile.repository.AIProfileRequestRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
@Profile("test", "codegen")
class DefaultAIGenerationClient(
    private val aiProfileRequestRepository: AIProfileRequestRepository
) : AIGenerationClient {

    @Value("\${ai.profile.placeholder-url:https://placehold.co/512x512/png?text=AI+Profile}")
    private lateinit var placeholderUrl: String

    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun requestGeneration(aiRequest: AIProfileRequest) {
        // Update status to PROCESSING immediately
        updateStatus(aiRequest.id, AIRequestStatus.PROCESSING)

        // Schedule completion after 5 seconds
        executor.schedule({
            try {
                completeGeneration(aiRequest.id, placeholderUrl)
            } catch (e: Exception) {
                failGeneration(aiRequest.id, e.message ?: "Unknown error")
            }
        }, 5, TimeUnit.SECONDS)
    }

    private fun updateStatus(requestId: Long, status: AIRequestStatus) {
        val request = aiProfileRequestRepository.findById(requestId).orElse(null) ?: return
        request.status = status
        aiProfileRequestRepository.save(request)
    }

    private fun completeGeneration(requestId: Long, resultUrl: String) {
        val request = aiProfileRequestRepository.findById(requestId).orElse(null) ?: return
        request.status = AIRequestStatus.COMPLETED
        request.resultUrl = resultUrl
        aiProfileRequestRepository.save(request)
    }

    private fun failGeneration(requestId: Long, reason: String) {
        val request = aiProfileRequestRepository.findById(requestId).orElse(null) ?: return
        request.status = AIRequestStatus.FAILED
        request.errorMessage = reason
        aiProfileRequestRepository.save(request)
    }
}
