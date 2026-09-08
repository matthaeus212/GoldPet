package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.*
import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.gamification.repository.BadgeRepository
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.gamification.repository.UserBadgeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed interface BadgeDeletionResult {
    object HardDeleted : BadgeDeletionResult
    object SoftDeleted : BadgeDeletionResult
}

@Service
class AdminGamificationService(
    private val badgeRepository: BadgeRepository,
    private val userBadgeRepository: UserBadgeRepository
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getBadges(): List<BadgeAdminResponse> {
        val earnedCountByBadgeId = userBadgeRepository.findBadgeIdEarnedCounts()
            .associate { it.getBadgeId() to it.getEarnedCount().toInt() }
        return badgeRepository.findAll().map { badge ->
            toBadgeAdminResponse(badge, earnedCountByBadgeId[badge.id] ?: 0)
        }
    }

    @Transactional
    fun createBadge(request: CreateBadgeRequest): BadgeAdminResponse {
        val badge = Badge(
            name = request.name,
            description = request.description,
            imageUrl = request.imageUrl ?: "",
            conditionType = request.conditionType?.let { BadgeConditionType.valueOf(it) },
            conditionValue = request.conditionValue,
            rewardGold = request.rewardGold,
            startDate = request.startDate?.let { LocalDate.parse(it).atStartOfDay() },
            endDate = request.endDate?.let { LocalDate.parse(it).atTime(23, 59, 59) },
            isRepeatable = request.isRepeatable ?: false,
            repeatCycle = request.repeatCycle
        )
        val saved = badgeRepository.save(badge)
        return toBadgeAdminResponse(saved, 0)
    }

    @Transactional
    fun deleteBadge(badgeId: Long): BadgeDeletionResult {
        val badge = badgeRepository.findById(badgeId)
            .orElseThrow { NotFoundException("Badge not found") }
        val earnedCount = userBadgeRepository.countByBadgeId(badgeId)
        return if (earnedCount > 0) {
            badge.isActive = false
            badgeRepository.save(badge)
            BadgeDeletionResult.SoftDeleted
        } else {
            badgeRepository.delete(badge)
            BadgeDeletionResult.HardDeleted
        }
    }

    @Transactional
    fun updateBadge(badgeId: Long, request: UpdateBadgeRequest): BadgeAdminResponse {
        val badge = badgeRepository.findById(badgeId)
            .orElseThrow { NotFoundException("Badge not found") }

        request.name?.let { badge.name = it }
        request.description?.let { badge.description = it }
        request.imageUrl?.let { badge.imageUrl = it }
        request.conditionType?.let { badge.conditionType = BadgeConditionType.valueOf(it) }
        request.conditionValue?.let { badge.conditionValue = it }
        request.isActive?.let { badge.isActive = it }
        request.rewardGold?.let { badge.rewardGold = it }
        request.startDate?.let { badge.startDate = LocalDate.parse(it).atStartOfDay() }
        request.endDate?.let { badge.endDate = LocalDate.parse(it).atTime(23, 59, 59) }
        request.isRepeatable?.let { badge.isRepeatable = it }
        request.repeatCycle?.let { badge.repeatCycle = it }

        val saved = badgeRepository.save(badge)
        val earnedCount = userBadgeRepository.countByBadgeId(saved.id)
        return toBadgeAdminResponse(saved, earnedCount.toInt())
    }

    private fun toBadgeAdminResponse(badge: Badge, earnedCount: Int): BadgeAdminResponse {
        return BadgeAdminResponse(
            id = badge.id,
            name = badge.name,
            description = badge.description,
            imageUrl = badge.imageUrl,
            conditionType = badge.conditionType?.name,
            conditionValue = badge.conditionValue,
            isActive = badge.isActive,
            rewardGold = badge.rewardGold,
            startDate = badge.startDate?.format(dateFormatter),
            endDate = badge.endDate?.format(dateFormatter),
            isRepeatable = badge.isRepeatable,
            repeatCycle = badge.repeatCycle,
            earnedCount = earnedCount
        )
    }
}
