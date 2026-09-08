package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.BoostResponse
import com.goldpet.domain.admin.controller.CohortConversion
import com.goldpet.domain.admin.controller.DauDay
import com.goldpet.domain.admin.controller.MatchRateResponse
import com.goldpet.domain.admin.controller.MissionCompletionDay
import com.goldpet.domain.admin.controller.MissionCompletionResponse
import com.goldpet.domain.admin.controller.ReengagementByType
import com.goldpet.domain.admin.controller.ReengagementResponse
import com.goldpet.domain.admin.controller.RetentionCohort
import com.goldpet.domain.admin.controller.RetentionResponse
import com.goldpet.domain.admin.controller.StreakCorrelationGroup
import com.goldpet.domain.admin.controller.StreakCorrelationResponse
import com.goldpet.domain.admin.controller.WindowMeta
import com.goldpet.domain.admin.repository.AdminMetricsRepository
import com.goldpet.domain.admin.repository.CohortConversionRow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

/**
 * Derive-first 포스트런치 메트릭 서비스(plan §W2). 모든 집계는 [AdminMetricsRepository] 의 native SQL 이며
 * 이 서비스는 윈도우 클램프 + rate 계산 + DTO 조립만 담당한다(MDE/유의성은 범위 밖).
 *
 * "하루" 경계는 캡처 테이블과 동일하게 KST(Asia/Seoul). 윈도우가 캡처 시작 이전으로 새면
 * effectiveFrom 을 캡처 시작으로 끌어올리고 [WindowMeta.windowClampedToCapture] 로 표시한다 —
 * 반쯤 채워진 D7 을 진짜 수치처럼 노출하지 않기 위함.
 */
@Service
class AdminMetricsService(
    private val adminMetricsRepository: AdminMetricsRepository,
) {
    private val kst = ZoneId.of("Asia/Seoul")

    private val streakNote =
        "correlational, selection-biased, not a controlled comparison; " +
            "streak status as of query time, not as of the retention window " +
            "(user_streaks.current_streak is mutable and resets on a gap)"
    private val reengagementNote =
        "UNIQUE(user_id, send_date) caps return-rate to one nudge_type per user per day"

    // ------------------------------------------------------------------
    // 1. match-rate (A/B)
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    fun getMatchRate(days: Int): MatchRateResponse {
        val since = parseDate(adminMetricsRepository.likeEventsDataAvailableSince())
        val window = buildWindow(days, since)

        val rows = adminMetricsRepository
            .matchRateByCohort(window.effectiveFrom.atStartOfDay())
            .associateBy { it.cohort }

        val treatment = toConversion("TREATMENT", rows["TREATMENT"])
        val control = toConversion("CONTROL", rows["CONTROL"])
        val none = toConversion("NONE", rows["NONE"])

        val totalLikeActions = treatment.likeActions + control.likeActions + none.likeActions
        val noneFraction = if (totalLikeActions == 0L) 0.0 else none.likeActions.toDouble() / totalLikeActions

        return MatchRateResponse(
            window = window,
            treatment = treatment,
            control = control,
            noneFraction = noneFraction,
        )
    }

    private fun toConversion(cohort: String, row: CohortConversionRow?): CohortConversion {
        val likeActions = row?.likeActions ?: 0L
        val matches = row?.matches ?: 0L
        return CohortConversion(
            cohort = cohort,
            likeActions = likeActions,
            matches = matches,
            matchRate = rate(matches, likeActions),
        )
    }

    // ------------------------------------------------------------------
    // 2. mission completion
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    fun getMissionCompletion(days: Int): MissionCompletionResponse {
        val since = parseDate(adminMetricsRepository.missionDataAvailableSince())
        val window = buildWindow(days, since)

        val dayRows = adminMetricsRepository.missionCompletionByDay(window.effectiveFrom).map {
            MissionCompletionDay(
                date = LocalDate.parse(it.date),
                missionsOffered = it.missionsOffered ?: 0L,
                completions = it.completions,
                uniqueCompleters = it.uniqueCompleters,
            )
        }
        return MissionCompletionResponse(window = window, days = dayRows)
    }

    // ------------------------------------------------------------------
    // 3. re-engagement
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    fun getReengagement(days: Int, returnWindowDays: Int): ReengagementResponse {
        val since = parseDate(adminMetricsRepository.reengagementDataAvailableSince())
        val window = buildWindow(days, since)

        val byType = adminMetricsRepository
            .reengagementReturnByType(window.effectiveFrom, returnWindowDays)
            .map {
                ReengagementByType(
                    nudgeType = it.nudgeType,
                    sends = it.sends,
                    returned = it.returned,
                    returnRate = rate(it.returned, it.sends),
                )
            }

        val totalSends = byType.sumOf { it.sends }
        val totalReturned = byType.sumOf { it.returned }

        val optOut = adminMetricsRepository.reengagementOptOut()
        val optOutFraction =
            if (optOut.total == 0L) 0.0 else optOut.optedOut.toDouble() / optOut.total

        return ReengagementResponse(
            window = window,
            returnWindowDays = returnWindowDays,
            byType = byType,
            totalSends = totalSends,
            totalReturned = totalReturned,
            overallReturnRate = rate(totalReturned, totalSends),
            optOutFraction = optOutFraction,
            optedOutUsers = optOut.optedOut,
            totalUsers = optOut.total,
            note = reengagementNote,
        )
    }

    // ------------------------------------------------------------------
    // 4. boost
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    fun getBoost(days: Int): BoostResponse {
        val since = parseDate(adminMetricsRepository.boostDataAvailableSince())
        val window = buildWindow(days, since)

        val summary = adminMetricsRepository.boostSummary(window.effectiveFrom.atStartOfDay())
        return BoostResponse(
            window = window,
            boostCount = summary.boostCount,
            goldSpent = summary.goldSpent,
            uniqueUsers = summary.uniqueUsers,
        )
    }

    // ------------------------------------------------------------------
    // 5. retention / DAU
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    fun getRetention(days: Int): RetentionResponse {
        val dauSince = parseDate(adminMetricsRepository.dauDataAvailableSince())
        val window = buildWindow(days, dauSince)
        val today = LocalDate.now(kst)

        // DAU has no data before capture → clamp to effectiveFrom.
        val dau = adminMetricsRepository.dauByDay(window.effectiveFrom).map {
            DauDay(date = LocalDate.parse(it.date), dau = it.dau)
        }

        // Signup cohorts legitimately predate DAU capture; do NOT clamp the cohort window —
        // the per-row maturity flags express whether each cohort's D1/D7 is trustworthy.
        val cohorts = adminMetricsRepository.signupCohortRetention(window.requestedFrom).map {
            val cohortDate = LocalDate.parse(it.cohortDate)
            RetentionCohort(
                cohortDate = cohortDate,
                cohortSize = it.cohortSize,
                d1Retained = it.d1Retained,
                d7Retained = it.d7Retained,
                d1Rate = rate(it.d1Retained, it.cohortSize),
                d7Rate = rate(it.d7Retained, it.cohortSize),
                d1Mature = isMeasurementMature(cohortDate, 1, dauSince, today),
                d7Mature = isMeasurementMature(cohortDate, 7, dauSince, today),
            )
        }

        return RetentionResponse(window = window, dau = dau, cohorts = cohorts)
    }

    // ------------------------------------------------------------------
    // 6. streak correlation (CORRELATIONAL)
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    fun getStreakCorrelation(days: Int): StreakCorrelationResponse {
        val dauSince = parseDate(adminMetricsRepository.dauDataAvailableSince())
        val window = buildWindow(days, dauSince)
        val today = LocalDate.now(kst)
        // Only cohorts whose D7 measurement day has already passed are comparable.
        val matureCutoff = today.minusDays(7)

        // Signup cohorts may predate DAU capture; use the full requested window (maturity is
        // bounded by matureCutoff = today-7, and only DAU-captured days contribute to d7Active).
        val groups = adminMetricsRepository
            .streakD7Correlation(window.requestedFrom, matureCutoff)
            .associateBy { it.hasStreak }

        return StreakCorrelationResponse(
            window = window,
            withStreak = toStreakGroup(true, groups[true]?.users, groups[true]?.d7Active),
            withoutStreak = toStreakGroup(false, groups[false]?.users, groups[false]?.d7Active),
            note = streakNote,
        )
    }

    private fun toStreakGroup(hasStreak: Boolean, users: Long?, d7Active: Long?): StreakCorrelationGroup {
        val u = users ?: 0L
        val a = d7Active ?: 0L
        return StreakCorrelationGroup(hasStreak = hasStreak, users = u, d7Active = a, d7Rate = rate(a, u))
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun parseDate(raw: String?): LocalDate? = raw?.let { LocalDate.parse(it) }

    private fun buildWindow(days: Int, dataAvailableSince: LocalDate?): WindowMeta {
        val to = LocalDate.now(kst)
        val requestedFrom = to.minusDays(days.toLong())
        val clamped = dataAvailableSince != null && dataAvailableSince.isAfter(requestedFrom)
        val effectiveFrom = if (clamped) dataAvailableSince!! else requestedFrom
        return WindowMeta(
            requestedFrom = requestedFrom,
            effectiveFrom = effectiveFrom,
            to = to,
            dataAvailableSince = dataAvailableSince,
            windowClampedToCapture = clamped,
        )
    }

    /**
     * day-N retention measurement is trustworthy only when the measurement day (cohort + N)
     * is both within the daily-active capture range and already in the past.
     */
    private fun isMeasurementMature(
        cohortDate: LocalDate,
        n: Long,
        dauSince: LocalDate?,
        today: LocalDate,
    ): Boolean {
        if (dauSince == null) return false
        val measurementDay = cohortDate.plusDays(n)
        return !measurementDay.isBefore(dauSince) && !measurementDay.isAfter(today)
    }

    private fun rate(numerator: Long, denominator: Long): Double? =
        if (denominator == 0L) null else numerator.toDouble() / denominator
}
