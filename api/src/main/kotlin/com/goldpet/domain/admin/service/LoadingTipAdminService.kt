package com.goldpet.domain.admin.service

import com.goldpet.domain.aiprofile.dto.AILoadingTipResponse
import com.goldpet.domain.aiprofile.dto.CreateLoadingTipRequest
import com.goldpet.domain.aiprofile.dto.UpdateLoadingTipRequest
import com.goldpet.domain.aiprofile.entity.AILoadingTip
import com.goldpet.domain.aiprofile.repository.AILoadingTipRepository
import com.goldpet.domain.common.exception.NotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LoadingTipAdminService(
    private val aiLoadingTipRepository: AILoadingTipRepository
) {

    @Cacheable(value = ["ai:loading-tips"], key = "'active'")
    fun getActiveTips(): List<AILoadingTipResponse> {
        return aiLoadingTipRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()
            .map { it.toResponse() }
    }

    fun getAllTips(): List<AILoadingTipResponse> {
        return aiLoadingTipRepository.findAllByOrderByDisplayOrderAsc()
            .map { it.toResponse() }
    }

    @Transactional
    @CacheEvict(value = ["ai:loading-tips"], allEntries = true)
    fun createTip(request: CreateLoadingTipRequest): AILoadingTipResponse {
        val tip = AILoadingTip(
            content = request.content,
            displayOrder = request.displayOrder
        )
        return aiLoadingTipRepository.save(tip).toResponse()
    }

    @Transactional
    @CacheEvict(value = ["ai:loading-tips"], allEntries = true)
    fun updateTip(tipId: Int, request: UpdateLoadingTipRequest): AILoadingTipResponse {
        val tip = aiLoadingTipRepository.findById(tipId)
            .orElseThrow { NotFoundException("로딩 팁을 찾을 수 없습니다.") }

        request.content?.let { tip.content = it }
        request.displayOrder?.let { tip.displayOrder = it }
        request.isActive?.let { tip.isActive = it }

        return aiLoadingTipRepository.save(tip).toResponse()
    }

    @Transactional
    @CacheEvict(value = ["ai:loading-tips"], allEntries = true)
    fun deleteTip(tipId: Int) {
        if (!aiLoadingTipRepository.existsById(tipId)) {
            throw NotFoundException("로딩 팁을 찾을 수 없습니다.")
        }
        aiLoadingTipRepository.deleteById(tipId)
    }

    private fun AILoadingTip.toResponse() = AILoadingTipResponse(
        id = this.id,
        content = this.content,
        displayOrder = this.displayOrder,
        isActive = this.isActive
    )
}
