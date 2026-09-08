package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.dto.*
import com.goldpet.domain.aiprofile.entity.AIRequestStatus
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.aiprofile.entity.AIStyle
import com.goldpet.domain.aiprofile.repository.AIProfileRequestRepository
import com.goldpet.domain.aiprofile.repository.AIStyleRepository
import com.goldpet.domain.gold.service.GoldService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class AdminAIProfileService(
    private val aiRequestRepository: AIProfileRequestRepository,
    private val aiStyleRepository: AIStyleRepository,
    private val goldService: GoldService
) {
    fun getRequests(
        page: Int,
        size: Int,
        status: AIRequestStatus?,
        type: String?,
        userId: Long?
    ): Page<AIRequestAdminResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return aiRequestRepository.findAllForAdmin(status, pageable)
            .map { AIRequestAdminResponse.from(it) }
    }

    fun getRequestDetail(requestId: Long): AIRequestAdminResponse {
        val request = aiRequestRepository.findById(requestId)
            .orElseThrow { NotFoundException("AI request not found: $requestId") }
        return AIRequestAdminResponse.from(request)
    }

    @Transactional
    fun retryRequest(requestId: Long): AIRequestAdminResponse {
        val request = aiRequestRepository.findById(requestId)
            .orElseThrow { NotFoundException("AI request not found: $requestId") }
        if (request.status != AIRequestStatus.FAILED) {
            throw BadRequestException("Only failed requests can be retried")
        }
        request.status = AIRequestStatus.PENDING
        request.errorMessage = null
        val saved = aiRequestRepository.save(request)
        return AIRequestAdminResponse.from(saved)
    }

    @Transactional
    fun refundRequest(requestId: Long) {
        val request = aiRequestRepository.findById(requestId)
            .orElseThrow { NotFoundException("AI request not found: $requestId") }
        if (request.status != AIRequestStatus.FAILED) {
            throw BadRequestException("Only failed requests can be refunded")
        }
        request.status = AIRequestStatus.REFUNDED
        aiRequestRepository.save(request)
        goldService.systemRefund(request.user.id, request.goldCost, "AI 프로필 생성 실패 환불 (관리자)", "AI_PROFILE_FAIL", requestId)
    }

    @Transactional
    fun forceFailRequest(requestId: Long) {
        val request = aiRequestRepository.findById(requestId)
            .orElseThrow { NotFoundException("AI request not found: $requestId") }
        if (request.status != AIRequestStatus.PENDING && request.status != AIRequestStatus.PROCESSING) {
            throw BadRequestException("Only pending or processing requests can be force-failed")
        }
        request.status = AIRequestStatus.FAILED
        request.errorMessage = "관리자에 의해 강제 실패 처리됨"
        aiRequestRepository.save(request)
        goldService.systemRefund(request.user.id, request.goldCost, "AI 프로필 생성 강제 실패 환불 (관리자)", "AI_PROFILE_FORCE_FAIL", requestId)
    }

    // Style Management
    fun getStyles(): List<AIStyleAdminResponse> {
        return aiStyleRepository.findAllByOrderByDisplayOrderAsc()
            .map { AIStyleAdminResponse.from(it) }
    }

    @Transactional
    fun createStyle(request: AIStyleAdminRequest): AIStyleAdminResponse {
        val style = AIStyle(
            id = request.id,
            name = request.name,
            description = request.description,
            previewUrl = request.previewUrl,
            goldCost = request.goldCost,
            isActive = request.isActive,
            displayOrder = request.displayOrder
        )
        return AIStyleAdminResponse.from(aiStyleRepository.save(style))
    }

    @Transactional
    fun updateStyle(styleId: String, request: AIStyleAdminRequest): AIStyleAdminResponse {
        val style = aiStyleRepository.findById(styleId)
            .orElseThrow { NotFoundException("Style not found: $styleId") }
        style.name = request.name
        style.description = request.description
        style.previewUrl = request.previewUrl
        style.goldCost = request.goldCost
        style.isActive = request.isActive
        style.displayOrder = request.displayOrder
        style.updatedAt = LocalDateTime.now()
        return AIStyleAdminResponse.from(aiStyleRepository.save(style))
    }

    @Transactional
    fun deleteStyle(styleId: String) {
        aiStyleRepository.deleteById(styleId)
    }

    @Transactional
    fun toggleStyle(styleId: String): AIStyleAdminResponse {
        val style = aiStyleRepository.findById(styleId)
            .orElseThrow { NotFoundException("Style not found: $styleId") }
        style.isActive = !style.isActive
        style.updatedAt = LocalDateTime.now()
        return AIStyleAdminResponse.from(aiStyleRepository.save(style))
    }

    fun getStats(): AIProfileStatsResponse {
        val all = aiRequestRepository.findAll()
        val total = all.size.toLong()
        val pending = all.count { it.status == AIRequestStatus.PENDING }.toLong()
        val completed = all.count { it.status == AIRequestStatus.COMPLETED }.toLong()
        val failed = all.count { it.status == AIRequestStatus.FAILED }.toLong()
        val refunded = all.count { it.status == AIRequestStatus.REFUNDED }.toLong()
        val totalGold = all.sumOf { it.goldCost.toLong() }
        val successRate = if (total > 0) completed.toDouble() / total * 100 else 0.0

        val dailyBreakdown = all
            .groupBy { it.createdAt.toLocalDate() }
            .map { (date, items) ->
                DailyStatEntry(date = date, count = items.size, totalDistanceKm = 0.0)
            }
            .sortedBy { it.date }

        val popularStyles = all
            .filter { it.stylePrompt != null }
            .groupBy { it.stylePrompt!! }
            .map { (style, items) ->
                StyleUsageEntry(styleId = style, styleName = style, count = items.size)
            }
            .sortedByDescending { it.count }
            .take(10)

        return AIProfileStatsResponse(
            totalRequests = total,
            pendingRequests = pending,
            completedRequests = completed,
            failedRequests = failed,
            refundedRequests = refunded,
            totalGoldSpent = totalGold,
            successRate = successRate,
            dailyBreakdown = dailyBreakdown,
            popularStyles = popularStyles
        )
    }
}
