package com.goldpet.domain.health.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.domain.health.dto.toPetContextDto
import com.goldpet.domain.health.event.StoolAnalysisRequestedEvent
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.walk.entity.AnalysisStatus
import com.goldpet.domain.walk.repository.StoolAnalysisRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime

@Component
@Profile("local", "dev", "prod")
class StoolAnalysisEventListener(
    private val stoolAnalysisRepository: StoolAnalysisRepository,
    private val petRepository: PetRepository,
    private val stoolVisionClient: StoolVisionClient,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("stoolAnalysisExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleAnalysis(event: StoolAnalysisRequestedEvent) {
        log.info("Handling StoolAnalysisRequestedEvent for analysisId={}", event.analysisId)

        val analysis = stoolAnalysisRepository.findById(event.analysisId).orElse(null)
        if (analysis == null) {
            log.error("StoolAnalysis not found for id={}", event.analysisId)
            return
        }

        // Mark ANALYZING
        analysis.status = AnalysisStatus.ANALYZING
        stoolAnalysisRepository.save(analysis)

        try {
            // Re-fetch pet to avoid lazy loading issues in async context
            val pet = petRepository.findById(analysis.pet.id).orElse(null)
            if (pet == null) {
                log.error("Pet not found for id={}", analysis.pet.id)
                markFailed(event.analysisId, "Pet not found")
                return
            }

            val petContext = pet.toPetContextDto()
            val result = stoolVisionClient.analyze(analysis.imageUrl, petContext)

            // Re-fetch to get latest state before updating
            val toUpdate = stoolAnalysisRepository.findById(event.analysisId).orElse(null) ?: return

            toUpdate.colorScore = result.colorScore
            toUpdate.consistencyScore = result.consistencyScore
            toUpdate.coatingScore = result.coatingScore
            toUpdate.contentsScore = result.contentsScore
            toUpdate.overallScore = result.overallScore
            toUpdate.colorAssessment = result.colorAssessment
            toUpdate.consistencyAssessment = result.consistencyAssessment
            toUpdate.coatingAssessment = result.coatingAssessment
            toUpdate.contentsAssessment = result.contentsAssessment
            toUpdate.healthSummary = result.healthSummary
            toUpdate.healthTips = objectMapper.writeValueAsString(result.healthTips)
            toUpdate.warnings = objectMapper.writeValueAsString(result.warnings)
            toUpdate.status = AnalysisStatus.COMPLETED
            toUpdate.analyzedAt = LocalDateTime.now()
            stoolAnalysisRepository.save(toUpdate)

            log.info("Stool analysis completed for analysisId={}", event.analysisId)

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            markFailed(event.analysisId, "Analysis interrupted")
        } catch (e: Exception) {
            log.error("Stool analysis failed for analysisId={}: {}", event.analysisId, e.message, e)
            markFailed(event.analysisId, e.message ?: "Unknown error")
        }
    }

    private fun markFailed(analysisId: Long, reason: String) {
        try {
            val analysis = stoolAnalysisRepository.findById(analysisId).orElse(null)
            if (analysis != null) {
                analysis.status = AnalysisStatus.FAILED
                analysis.errorMessage = reason
                stoolAnalysisRepository.save(analysis)
            }
        } catch (e: Exception) {
            log.error("Failed to mark analysis FAILED for id={}: {}", analysisId, e.message)
        }
    }
}
