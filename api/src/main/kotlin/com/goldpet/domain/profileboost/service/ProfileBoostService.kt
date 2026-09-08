package com.goldpet.domain.profileboost.service

import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.dto.SpendRequest
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.profileboost.dto.ActiveBoostResponse
import com.goldpet.domain.profileboost.dto.ProfileBoostResponse
import com.goldpet.domain.profileboost.entity.ProfileBoost
import com.goldpet.domain.profileboost.repository.ProfileBoostRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 프로필 부스트(골드 sink). 구매 시 GoldService.spendGold()로 골드 차감 후 고정 윈도우 부스트 기록.
 * 친구 랭킹(W1, 타 워커 소유) 통합은 [boostedUserIds] 훅으로 노출 — 랭킹 측에서 가중치 적용.
 */
@Service
@Transactional(readOnly = true)
class ProfileBoostService(
    private val profileBoostRepository: ProfileBoostRepository,
    private val goldService: GoldService,
    private val systemSettingService: SystemSettingService
) {
    private val log = LoggerFactory.getLogger(ProfileBoostService::class.java)

    companion object {
        const val COST_KEY = "profile.boost.cost"
        const val DEFAULT_COST = 30
        const val DURATION_KEY = "profile.boost.duration_minutes"
        const val DEFAULT_DURATION_MINUTES = 30
        const val REFERENCE_TYPE = "PROFILE_BOOST"
    }

    /**
     * 부스트 구매: 골드 차감(잔액 부족 시 BadRequestException) 후 부스트 윈도우 생성.
     * spendGold가 먼저 실행되어, 잔액 부족 시 부스트 row가 생성되지 않음.
     */
    @Transactional
    fun purchaseBoost(userId: Long): ProfileBoostResponse {
        val cost = systemSettingService.getInt(COST_KEY, DEFAULT_COST)
        val durationMinutes = systemSettingService.getInt(DURATION_KEY, DEFAULT_DURATION_MINUTES)

        // 골드 차감(AI 프로필 spend 선례). 잔액 부족 시 여기서 예외 → 부스트 미생성.
        goldService.spendGold(
            userId,
            SpendRequest(
                amount = cost,
                description = "프로필 노출 부스트 (${durationMinutes}분)",
                referenceType = REFERENCE_TYPE,
                referenceId = null
            )
        )

        val now = LocalDateTime.now()
        val boost = profileBoostRepository.save(
            ProfileBoost(
                userId = userId,
                startedAt = now,
                expiresAt = now.plusMinutes(durationMinutes.toLong()),
                goldCost = cost
            )
        )
        log.info("Profile boost purchased: userId={}, cost={}, expiresAt={}", userId, cost, boost.expiresAt)
        return ProfileBoostResponse.from(boost, now)
    }

    /** 현재 활성 부스트(없으면 active=false). */
    fun getActiveBoost(userId: Long): ActiveBoostResponse {
        val now = LocalDateTime.now()
        val active = profileBoostRepository
            .findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(userId, now)
        return ActiveBoostResponse(
            active = active != null,
            boost = active?.let { ProfileBoostResponse.from(it, now) }
        )
    }

    /**
     * 친구 랭킹 통합 훅: 현재 부스트 활성 유저 id 집합.
     * W1 랭킹(타 워커)에서 후보 재정렬 시 가산점/우선노출에 사용.
     */
    fun boostedUserIds(now: LocalDateTime = LocalDateTime.now()): Set<Long> {
        return profileBoostRepository.findAllByExpiresAtAfter(now).map { it.userId }.toSet()
    }
}
