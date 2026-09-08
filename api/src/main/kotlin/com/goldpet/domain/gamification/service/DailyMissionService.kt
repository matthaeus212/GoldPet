package com.goldpet.domain.gamification.service

import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.gamification.dto.DailyMissionResponse
import com.goldpet.domain.gamification.dto.MissionItem
import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.gamification.entity.DailyMissionSet
import com.goldpet.domain.gamification.repository.BadgeRepository
import com.goldpet.domain.gamification.repository.DailyMissionSetRepository
import com.goldpet.domain.gamification.repository.UserBadgeRepository
import com.goldpet.domain.user.repository.UserRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

/**
 * 데일리 미션 = repeatable DAILY 배지의 표현 (신규 보상경로 없음).
 * - 회전: KST 자정 글로벌 1회 잡 → daily_mission_set 1행 생성(전 유저 공통).
 * - 진행도: user_badges(DAILY cycleKey = mission_date) 읽기 조인.
 * - 보상/동시성/골드 cap은 BadgeAwardService가 처리(기존 award 경로 재사용).
 */
@Service
@Transactional(readOnly = true)
class DailyMissionService(
    private val dailyMissionSetRepository: DailyMissionSetRepository,
    private val badgeRepository: BadgeRepository,
    private val userRepository: UserRepository,
    private val userBadgeRepository: UserBadgeRepository
) {
    private val log = LoggerFactory.getLogger(DailyMissionService::class.java)

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        const val MISSIONS_PER_DAY = 3
        private const val DAILY_CYCLE = "DAILY"
        val MISSION_CONDITION_TYPES = setOf(
            BadgeConditionType.WALK_DISTANCE_TOTAL,
            BadgeConditionType.COMMUNITY_POST,
            BadgeConditionType.CHECK_IN
        )
    }

    /**
     * 회전 cron — KST 00:05 (기존 03:00-04:40 혼잡창 회피). 하루 1행만 생성(idempotent).
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "dailyMissionRotation", lockAtMostFor = "15m")
    @Transactional
    fun rotateDailyMissions() {
        val today = LocalDate.now(KST)
        val set = ensureMissionSet(today)
        log.info("Daily mission rotation: date={}, badgeIds={}", today, set.badgeIds)
    }

    /**
     * 그날 mission set을 보장(없으면 생성). mission_date 유니크 + race catch로 cron/read 동시생성 안전.
     */
    @Transactional
    fun ensureMissionSet(date: LocalDate): DailyMissionSet {
        dailyMissionSetRepository.findByMissionDate(date)?.let { return it }

        val candidates = badgeRepository
            .findAllByIsRepeatableTrueAndRepeatCycleAndIsActiveTrue(DAILY_CYCLE)
            .filter { it.conditionType in MISSION_CONDITION_TYPES }
            .sortedBy { it.id }
        val selected = selectRotation(candidates, date).map { it.id }

        return try {
            dailyMissionSetRepository.saveAndFlush(
                DailyMissionSet(missionDate = date, badgeIds = selected)
            )
        } catch (ex: DataIntegrityViolationException) {
            // 동시 생성(cron + lazy read) — 유니크 mission_date 충돌 시 기존 행 사용.
            dailyMissionSetRepository.findByMissionDate(date) ?: throw ex
        }
    }

    /** 후보가 3개 초과면 날짜 기반 결정적 회전으로 3개 선택. */
    private fun selectRotation(candidates: List<Badge>, date: LocalDate): List<Badge> {
        if (candidates.size <= MISSIONS_PER_DAY) return candidates
        val offset = (date.toEpochDay() % candidates.size).toInt()
        return (0 until MISSIONS_PER_DAY).map { candidates[(offset + it) % candidates.size] }
    }

    /**
     * 오늘의 미션 + 진행도. set이 없으면 lazy-create(cron 미발화/첫 배포 대비).
     */
    @Transactional
    fun getTodayMissions(userId: Long): DailyMissionResponse {
        if (!userRepository.existsById(userId)) throw NotFoundException("User not found")

        val today = LocalDate.now(KST)
        val cycleKey = today.toString()
        val missionSet = ensureMissionSet(today)
        val badgeIds = missionSet.badgeIds
        if (badgeIds.isEmpty()) return DailyMissionResponse(today, emptyList())

        val badgesById = badgeRepository.findAllById(badgeIds).associateBy { it.id }
        val progressByBadgeId = userBadgeRepository.findByUserIdAndCycleKey(userId, cycleKey)
            .associateBy { it.badge.id }

        val missions = badgeIds.mapNotNull { badgeId ->
            val badge = badgesById[badgeId] ?: return@mapNotNull null
            val progress = progressByBadgeId[badgeId]
            MissionItem(
                badgeId = badge.id,
                name = badge.name,
                description = badge.description,
                imageUrl = badge.imageUrl,
                conditionType = badge.conditionType?.name,
                conditionValue = badge.conditionValue,
                rewardGold = badge.rewardGold,
                currentValue = progress?.currentValue,
                completed = progress != null,
                completedAt = progress?.createdAt
            )
        }
        return DailyMissionResponse(today, missions)
    }
}
