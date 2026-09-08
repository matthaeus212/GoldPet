package com.goldpet.domain.aiprofile.service

import com.goldpet.domain.aiprofile.entity.AIRequestStatus
import com.goldpet.domain.aiprofile.repository.AIProfileRequestRepository
import com.goldpet.domain.aiprofile.repository.AIStyleRepository
import com.goldpet.domain.common.util.InMemoryMultipartFile
import com.goldpet.domain.file.service.FileService
import com.goldpet.domain.gold.service.GoldService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Component
@Profile("local", "dev", "prod")
class AIGenerationEventListener(
    private val aiProfileRequestRepository: AIProfileRequestRepository,
    private val aiStyleRepository: AIStyleRepository,
    private val googleImagenApiClient: GoogleImagenApiClient,
    private val promptBuilder: AIPromptBuilder,
    private val goldService: GoldService,
    private val fileService: FileService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("aiGenerationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleGeneration(event: AIGenerationEvent) {
        log.info("Handling AIGenerationEvent for requestId={}", event.requestId)

        val request = aiProfileRequestRepository.findById(event.requestId).orElse(null)
        if (request == null) {
            log.error("AIProfileRequest not found for id={}", event.requestId)
            return
        }

        // Mark PROCESSING
        request.status = AIRequestStatus.PROCESSING
        aiProfileRequestRepository.save(request)

        try {
            // Build prompt
            val style = request.stylePrompt?.let { aiStyleRepository.findById(it).orElse(null) }
            val presetId = style?.presetId
            val prompt = promptBuilder.buildPrompt(request.pet, request.stylePrompt, presetId, request.petType)

            log.info("Generating image with Google Imagen for requestId={}, hasSourceImage={}", event.requestId, request.sourceImageUrl.isNotBlank())

            // Generate image (synchronous - returns result directly, with optional reference image)
            val rawImageBytes = googleImagenApiClient.generateImage(prompt, request.sourceImageUrl)

            // Resize to 512x512 and compress as JPEG
            val jpegBytes = resizeAndCompress(rawImageBytes, 512, 0.85f)
            log.info("Image resized: {}KB -> {}KB for requestId={}", rawImageBytes.size / 1024, jpegBytes.size / 1024, event.requestId)

            // T1-7a: FileService 경유 업로드 → FileAttachment 로우 생성 + variant async 파이프라인 자동 실행.
            // 과거 `ai-profiles/{requestId}.jpg` 직접 PUT 경로는 고아 URL 이었음 — 이제
            // FileAttachment 를 남겨 T1-1.x lookup 으로 variant 공급 가능.
            val multipart = InMemoryMultipartFile(
                bytes = jpegBytes,
                originalFilename = "ai-profile-${event.requestId}.jpg",
                mimeType = "image/jpeg",
            )
            val fileAttachment = fileService.storeFile(
                file = multipart,
                userId = request.user.id,
                category = "ai-profile",
            )
            val finalUrl = fileAttachment.url

            // Mark COMPLETED
            val completed = aiProfileRequestRepository.findById(event.requestId).orElse(null) ?: return
            completed.status = AIRequestStatus.COMPLETED
            completed.resultUrl = finalUrl
            aiProfileRequestRepository.save(completed)

            log.info("AI generation completed for requestId={}, resultUrl={}, attachmentId={}", event.requestId, finalUrl, fileAttachment.id)

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            failAndRefund(event.requestId, request.user.id, request.goldCost, "Generation interrupted")
        } catch (e: Exception) {
            log.error("AI generation failed for requestId={}: {}", event.requestId, e.message, e)
            failAndRefund(event.requestId, request.user.id, request.goldCost, e.message ?: "Unknown error")
        }
    }

    private fun resizeAndCompress(imageBytes: ByteArray, targetSize: Int, quality: Float): ByteArray {
        val original = ImageIO.read(ByteArrayInputStream(imageBytes))
        val resized = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.drawImage(original.getScaledInstance(targetSize, targetSize, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
        g.dispose()

        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val param = writer.defaultWriteParam.apply {
            compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality
        }
        writer.output = ImageIO.createImageOutputStream(out)
        writer.write(null, javax.imageio.IIOImage(resized, null, null), param)
        writer.dispose()
        return out.toByteArray()
    }

    private fun failAndRefund(requestId: Long, userId: Long, goldCost: Int, reason: String) {
        try {
            val req = aiProfileRequestRepository.findById(requestId).orElse(null)
            if (req != null) {
                req.status = AIRequestStatus.FAILED
                req.errorMessage = reason
                aiProfileRequestRepository.save(req)
            }
        } catch (e: Exception) {
            log.error("Failed to mark request FAILED for id={}: {}", requestId, e.message)
        }

        try {
            goldService.systemRefund(
                userId = userId,
                amount = goldCost,
                description = "AI 프로필 생성 실패 자동 환불",
                referenceType = "AI_PROFILE_FAIL",
                referenceId = requestId
            )
            log.info("Auto-refunded {} gold to userId={} for failed requestId={}", goldCost, userId, requestId)
        } catch (e: Exception) {
            log.error("Failed to auto-refund for userId={} requestId={}: {}", userId, requestId, e.message)
        }
    }
}
