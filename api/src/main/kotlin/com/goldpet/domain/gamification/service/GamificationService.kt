package com.goldpet.domain.gamification.service

import com.goldpet.domain.gamification.dto.BadgeResponse
import com.goldpet.domain.gamification.repository.BadgeRepository
import com.goldpet.domain.gamification.repository.UserBadgeRepository
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GamificationService(
    private val userRepository: UserRepository,
    private val badgeRepository: BadgeRepository,
    private val userBadgeRepository: UserBadgeRepository
) {

    fun getAllBadgesWithStatus(userId: Long): List<BadgeResponse> {
        val user = getUser(userId)
        val allBadges = badgeRepository.findAllByIsActiveTrue()
        val userBadges = userBadgeRepository.findAllByUser(user)
        val earnedMap = userBadges.groupBy { it.badge.id }

        return allBadges.map { badge ->
            val earned = earnedMap[badge.id]
            val latestAcquired = earned?.maxByOrNull { it.createdAt }
            BadgeResponse(
                id = badge.id,
                name = badge.name,
                description = badge.description,
                imageUrl = badge.imageUrl,
                conditionType = badge.conditionType?.name,
                conditionValue = badge.conditionValue,
                rewardGold = badge.rewardGold,
                startDate = badge.startDate,
                endDate = badge.endDate,
                isRepeatable = badge.isRepeatable,
                repeatCycle = badge.repeatCycle,
                acquiredAt = latestAcquired?.createdAt
            )
        }
    }

    fun getMyBadges(userId: Long): List<BadgeResponse> {
        val user = getUser(userId)
        val userBadges = userBadgeRepository.findAllByUser(user)
        return userBadges.map { BadgeResponse.from(it) }
    }

    private fun getUser(userId: Long): User {
        return userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
    }
}
