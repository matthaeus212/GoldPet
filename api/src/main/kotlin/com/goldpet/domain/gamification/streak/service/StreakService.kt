package com.goldpet.domain.gamification.streak.service

import com.goldpet.domain.gamification.streak.dto.StreakResponse
import com.goldpet.domain.gamification.streak.dto.StreakResult
import com.goldpet.domain.gamification.streak.entity.UserStreak
import com.goldpet.domain.gamification.streak.repository.UserStreakRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * WALK 연속 산책 스트릭 서비스 (W2a, plan §2.2).
 *
 * ## 갱신 경로
 * `WalkCompletedEvent` AFTER_COMMIT 리스너에서 [recordWalk] 단일 호출.
 * - `REQUIRES_NEW`: 원본 산책 tx 는 이미 커밋됨 → 새 tx 필요(CourseWalkEventListener 선례).
 * - 반환된 [StreakResult] 의 값은 커밋된 스트릭 값 → 리스너가 walk-완료 푸시와 병합(한 산책=한 푸시).
 *
 * ## "하루" 경계
 * `ZoneId.of("Asia/Seoul")` 명시(JVM 기본 상속 금지). `last_active_date` vs `LocalDate.now(KST)`:
 * 같은 날=무변화, 어제=+1, 그 이전=리셋(프리즈 차감 후).
 *
 * ## 프리즈
 * 주 1회(ISO week, BadgeAwardService WEEKLY cycleKey 정의와 동일) 무료. 마지막 활동 주차와 오늘 주차가
 * 다르면 프리즈 1개로 재충전. 1일 공백 시 프리즈 소모로 스트릭 유지.
 *
 * ## 콜드스타트
 * 기존 유저 lazy-create — 첫 적격 산책 시 row 생성(없으면 streak 0). 과거 backfill 없음.
 */
@Service
class StreakService(
    private val userStreakRepository: UserStreakRepository,
    private val clock: Clock = Clock.system(KST)
) {
    private val log = LoggerFactory.getLogger(StreakService::class.java)

    /**
     * 스트릭 위젯용 읽기 조회. row 부재(콜드스타트) 시 0-스트릭([StreakResponse.empty]) 반환.
     */
    @Transactional(readOnly = true)
    fun getMyStreak(userId: Long): StreakResponse {
        val streak = userStreakRepository.findByUserId(userId) ?: return StreakResponse.empty()
        return StreakResponse.from(streak, LocalDate.now(clock))
    }

    /**
     * 산책 1회를 스트릭에 반영하고 커밋된 결과를 반환한다.
     * REQUIRES_NEW — AFTER_COMMIT 리스너에서 호출되어 자체 tx 를 개설.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordWalk(userId: Long): StreakResult {
        val today = LocalDate.now(clock)

        val existing = userStreakRepository.findByUserId(userId)
        if (existing == null) {
            // 콜드스타트 lazy-create: 첫 산책 → streak 1, 주간 프리즈 1 부여.
            val created = UserStreak(
                userId = userId,
                currentStreak = 1,
                longestStreak = 1,
                lastActiveDate = today,
                freezeCount = 1
            )
            return try {
                userStreakRepository.save(created)
                StreakResult(
                    currentStreak = 1,
                    longestStreak = 1,
                    lastActiveDate = today,
                    incremented = true,
                    freezeConsumed = false,
                    reset = false,
                    newRecord = true
                )
            } catch (e: DataIntegrityViolationException) {
                // 동시 산책 race: 다른 tx 가 먼저 생성 → 재조회 후 갱신 경로로 진행.
                log.debug("user_streaks lazy-create race for userId={}, re-fetching", userId)
                val refetched = userStreakRepository.findByUserId(userId)
                    ?: return StreakResult(1, 1, today, incremented = true, freezeConsumed = false, reset = false, newRecord = true)
                applyTransition(refetched, today)
            }
        }

        return applyTransition(existing, today)
    }

    /**
     * 순수 상태 전이 — 테스트 용이성을 위해 분리. 엔티티를 in-place 갱신하고 결과를 반환.
     */
    fun applyTransition(streak: UserStreak, today: LocalDate): StreakResult {
        val last = streak.lastActiveDate

        // 같은 날 중복 산책 → 무변화.
        if (last != null && !today.isAfter(last)) {
            return StreakResult(
                currentStreak = streak.currentStreak,
                longestStreak = streak.longestStreak,
                lastActiveDate = last,
                incremented = false,
                freezeConsumed = false,
                reset = false,
                newRecord = false
            )
        }

        // 주간 프리즈 재충전: 마지막 활동 주차 != 오늘 주차 → 프리즈 1로 리셋(스택 없음).
        if (last == null || isoWeekKey(last) != isoWeekKey(today)) {
            streak.freezeCount = 1
        }

        val daysBetween = if (last == null) Long.MAX_VALUE else today.toEpochDay() - last.toEpochDay()

        var freezeConsumed = false
        var reset = false
        when {
            daysBetween == 1L -> {
                // 어제 활동 → 연속.
                streak.currentStreak += 1
            }
            daysBetween == 2L && streak.freezeCount >= 1 -> {
                // 1일 공백 → 프리즈 소모로 유지(오늘이 새 활동일).
                streak.freezeCount -= 1
                streak.currentStreak += 1
                freezeConsumed = true
            }
            else -> {
                // 2일 이상 공백(또는 프리즈 없음) → 리셋.
                streak.currentStreak = 1
                reset = true
            }
        }

        val newRecord = streak.currentStreak > streak.longestStreak
        if (newRecord) {
            streak.longestStreak = streak.currentStreak
        }
        streak.lastActiveDate = today

        return StreakResult(
            currentStreak = streak.currentStreak,
            longestStreak = streak.longestStreak,
            lastActiveDate = today,
            incremented = true,
            freezeConsumed = freezeConsumed,
            reset = reset,
            newRecord = newRecord
        )
    }

    /** BadgeAwardService.getCycleKey("WEEKLY") 와 동일 포맷 — ISO week 경계 일치. */
    private fun isoWeekKey(date: LocalDate): String {
        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        return "${date.year}-W${week.toString().padStart(2, '0')}"
    }

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
