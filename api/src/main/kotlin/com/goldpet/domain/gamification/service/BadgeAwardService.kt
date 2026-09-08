package com.goldpet.domain.gamification.service

import com.goldpet.domain.checkin.repository.CheckInRepository
import com.goldpet.domain.community.repository.CommunityCommentRepository
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.friend.repository.MatchRepository
import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.gamification.entity.UserBadge
import com.goldpet.domain.gamification.repository.BadgeRepository
import com.goldpet.domain.gamification.repository.UserBadgeRepository
import com.goldpet.domain.gold.entity.GoldTransaction
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.repository.GoldTransactionRepository
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.repository.WalkRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields

@Service
class BadgeAwardService(
    private val badgeRepository: BadgeRepository,
    private val userBadgeRepository: UserBadgeRepository,
    private val userRepository: UserRepository,
    private val petRepository: PetRepository,
    private val walkRepository: WalkRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val communityCommentRepository: CommunityCommentRepository,
    private val checkInRepository: CheckInRepository,
    private val matchRepository: MatchRepository,
    private val goldTransactionRepository: GoldTransactionRepository,
    private val systemSettingService: SystemSettingService
) {
    private val log = LoggerFactory.getLogger(BadgeAwardService::class.java)

    companion object {
        /** 데일리 미션 골드 일 합산 cap (plan §2.3 / 인플레 완화). SystemSetting `mission.daily.gold_cap`로 튜닝. */
        const val DAILY_MISSION_GOLD_CAP = 20

        /** 사이클 키/캡 집계는 JVM 기본존 금지 — DailyMissionService·sumDailyRewardGoldGiven 과 동일 KST 고정. */
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }

    /**
     * 뱃지 award + 골드 지급.
     *
     * ## 동시성 (Critic #12)
     * 시작 시 [UserRepository.findByIdForUpdate] 로 user row 를 잠가 **동일 유저의 동시 award 를 직렬화**한다.
     * 효과:
     *  1. 동시-insert 레이스 제거 → `(user_id, badge_id, cycle_key)` 유니크(V9) 위반(DataIntegrityViolationException)이
     *     애초에 발생하지 않음 → 호출자(WalkService 등)의 외부 tx 가 rollback-only 로 오염되지 않는다.
     *     (REQUIRES_NEW 격리 대신 lock 직렬화를 택한 이유: WalkService.grantReward 가 이미 같은 user row 를
     *      외부 tx 에서 잠그고 user.goldBalance 를 변경하므로, 별도 tx 로 분리하면 self-deadlock + goldBalance lost-update 발생.)
     *  2. 데일리 골드 cap read-modify-write 직렬화 → 동시 미션이 cap(20) 을 초과 지급하지 못함.
     * WalkService 경로에선 grantReward 가 이미 같은 lock 을 보유 → 동일 tx 재진입(reentrant)이라 추가 대기 없음.
     */
    @Transactional
    fun checkAndAwardBadges(userId: Long, triggerType: BadgeConditionType): List<Badge> {
        val candidates = badgeRepository.findAllByConditionTypeAndIsActiveTrue(triggerType)
        if (candidates.isEmpty()) return emptyList()

        // user row 잠금으로 동일 유저 동시 award 직렬화 (insert 레이스 + 골드 cap 레이스 동시 차단)
        val user = userRepository.findByIdForUpdate(userId).orElse(null) ?: return emptyList()

        val now = LocalDateTime.now()
        val currentValue = getCurrentValue(userId, triggerType)
        val awarded = mutableListOf<Badge>()

        for (badge in candidates) {
            val conditionValue = badge.conditionValue ?: continue

            // Period check: skip if badge has date range and current time is outside
            if (badge.startDate != null && now.isBefore(badge.startDate)) continue
            if (badge.endDate != null && now.isAfter(badge.endDate)) continue

            val cycleKey = if (badge.isRepeatable) getCycleKey(badge.repeatCycle) else null

            // Check if already awarded (for this cycle if repeatable)
            if (badge.isRepeatable) {
                if (userBadgeRepository.existsByUserAndBadgeAndCycleKey(user, badge, cycleKey)) continue
            } else {
                if (userBadgeRepository.existsByUserAndBadge(user, badge)) continue
            }

            if (currentValue >= conditionValue) {
                // Concurrency: (user_id, badge_id, cycle_key) 유니크(V9)가 1회/주기 보장.
                // existsBy 통과 후 동시삽입 레이스는 DataIntegrityViolationException으로 잡아 골드 지급 skip.
                val userBadge = try {
                    userBadgeRepository.saveAndFlush(
                        UserBadge(
                            user = user,
                            badge = badge,
                            currentValue = currentValue.toInt(),
                            cycleKey = cycleKey
                        )
                    )
                } catch (ex: DataIntegrityViolationException) {
                    log.info(
                        "Badge award race lost (concurrent insert): userId={}, badge={} (id={}), cycleKey={} — skipping gold",
                        userId, badge.name, badge.id, cycleKey
                    )
                    continue
                }
                awarded.add(badge)
                log.info("Badge awarded: userId={}, badge={} (id={}), cycleKey={}", userId, badge.name, badge.id, cycleKey)

                // Give reward gold (데일리 미션은 하루 합산 cap 적용)
                val rewardGold = badge.rewardGold ?: 0
                if (rewardGold > 0) {
                    val grant = if (badge.isRepeatable && badge.repeatCycle == "DAILY") {
                        val cap = systemSettingService.getInt("mission.daily.gold_cap", DAILY_MISSION_GOLD_CAP)
                        val alreadyGiven = userBadgeRepository.sumDailyRewardGoldGiven(userId, cycleKey).toInt()
                        (cap - alreadyGiven).coerceIn(0, rewardGold)
                    } else {
                        rewardGold
                    }
                    if (grant > 0) {
                        user.goldBalance += grant
                        goldTransactionRepository.save(
                            GoldTransaction(
                                user = user,
                                type = TransactionType.REWARD,
                                amount = grant,
                                balanceAfter = user.goldBalance,
                                description = "뱃지 획득 보상: ${badge.name}"
                            )
                        )
                        userRepository.save(user)
                        userBadge.rewardGoldGiven = grant
                        userBadgeRepository.save(userBadge)
                    }
                }
            }
        }
        return awarded
    }

    private fun getCurrentValue(userId: Long, type: BadgeConditionType): Long {
        return when (type) {
            BadgeConditionType.PET_REGISTER -> petRepository.countByOwnerId(userId)
            BadgeConditionType.WALK_COUNT -> walkRepository.countByUserId(userId)
            BadgeConditionType.WALK_DISTANCE_TOTAL -> walkRepository.sumDistanceByUserId(userId).toLong()
            BadgeConditionType.COMMUNITY_POST -> communityPostRepository.countByUserId(userId)
            BadgeConditionType.COMMUNITY_COMMENT -> communityCommentRepository.countByUserId(userId)
            BadgeConditionType.CHECK_IN -> checkInRepository.countByUserId(userId)
            BadgeConditionType.FRIEND_MATCH -> matchRepository.countByUserId(userId)
        }
    }

    /**
     * 반복 사이클 키. **KST 명시** — JVM 기본존 상속 금지(Critic #12).
     * 데일리 cap 집계([UserBadgeRepository.sumDailyRewardGoldGiven]) 및 DailyMissionService 와 동일 기준.
     */
    private fun getCycleKey(repeatCycle: String?): String? {
        val today = LocalDate.now(KST)
        return when (repeatCycle) {
            "DAILY" -> today.toString() // "2026-02-18"
            "WEEKLY" -> {
                val week = today.get(WeekFields.ISO.weekOfWeekBasedYear())
                "${today.year}-W${week.toString().padStart(2, '0')}" // "2026-W08"
            }
            "MONTHLY" -> "${today.year}-${today.monthValue.toString().padStart(2, '0')}" // "2026-02"
            else -> null
        }
    }
}
