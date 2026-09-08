package com.goldpet.domain.admin.repository

import com.goldpet.domain.metrics.entity.LikeEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Read-only native-SQL aggregations over the W1/W2 capture tables (T4 metrics, plan §W2).
 *
 * Anchored to [LikeEvent] only to satisfy Spring Data's JPA repository contract — every
 * query here is `nativeQuery = true` and reads tables directly, so the root entity type is
 * irrelevant. **All queries are append-only/read-only**; this repository never mutates and
 * does not touch the other workers' write repositories ([com.goldpet.domain.metrics.repository.LikeEventRepository],
 * [com.goldpet.domain.metrics.repository.UserDailyActiveRepository]).
 *
 * Aliases are double-quoted (camelCase) so PostgreSQL preserves their case and Spring Data's
 * interface-projection binding matches the getter names exactly.
 */
interface AdminMetricsRepository : JpaRepository<LikeEvent, Long> {

    // ------------------------------------------------------------------
    // 1. match-rate (A/B) — like_events LIKE-action funnel by cohort
    // ------------------------------------------------------------------
    // Denominator = each LIKE-action row (a cancel→relike is a new LIKE).
    // Numerator    = LIKE rows whose is_match was true at like-time (point-in-time).
    // Rows are returned for TREATMENT / CONTROL / NONE; the service excludes NONE from
    // the A/B comparison and uses it only for the noneFraction validity gauge.
    @Query(
        value = """
            SELECT cohort                                                      AS "cohort",
                   COUNT(*) FILTER (WHERE action = 'LIKE')                      AS "likeActions",
                   COUNT(*) FILTER (WHERE action = 'LIKE' AND is_match)         AS "matches"
            FROM like_events
            WHERE occurred_at >= :since
            GROUP BY cohort
        """,
        nativeQuery = true,
    )
    fun matchRateByCohort(@Param("since") since: LocalDateTime): List<CohortConversionRow>

    @Query(value = "SELECT TO_CHAR(MIN(occurred_at), 'YYYY-MM-DD') FROM like_events", nativeQuery = true)
    fun likeEventsDataAvailableSince(): String?

    // ------------------------------------------------------------------
    // 2. mission completion — user_badges(DAILY cycle_key = mission_date) vs daily_mission_set
    // ------------------------------------------------------------------
    // cycle_key for DAILY badges == the KST mission day rendered 'YYYY-MM-DD'
    // (DailyMissionService: cycleKey = today.toString()).
    @Query(
        value = """
            SELECT TO_CHAR(dms.mission_date, 'YYYY-MM-DD')   AS "date",
                   jsonb_array_length(dms.badge_ids)         AS "missionsOffered",
                   COUNT(ub.id)                              AS "completions",
                   COUNT(DISTINCT ub.user_id)               AS "uniqueCompleters"
            FROM daily_mission_set dms
            LEFT JOIN user_badges ub
              ON ub.cycle_key = TO_CHAR(dms.mission_date, 'YYYY-MM-DD')
             AND ub.badge_id IN (SELECT jsonb_array_elements_text(dms.badge_ids)::bigint)
            WHERE dms.mission_date >= :sinceDate
            GROUP BY dms.mission_date, dms.badge_ids
            ORDER BY dms.mission_date
        """,
        nativeQuery = true,
    )
    fun missionCompletionByDay(@Param("sinceDate") sinceDate: LocalDate): List<MissionCompletionRow>

    @Query(value = "SELECT TO_CHAR(MIN(mission_date), 'YYYY-MM-DD') FROM daily_mission_set", nativeQuery = true)
    fun missionDataAvailableSince(): String?

    // ------------------------------------------------------------------
    // 3. re-engagement — reengagement_sends JOIN user_daily_active (returned within N days)
    // ------------------------------------------------------------------
    // UNIQUE(user_id, send_date) caps this to one nudge_type per user per day, so
    // return-rate-by-type is a 1-nudge/user/day funnel (documented in the response).
    @Query(
        value = """
            SELECT s.nudge_type                              AS "nudgeType",
                   COUNT(*)                                  AS "sends",
                   COUNT(*) FILTER (WHERE EXISTS (
                       SELECT 1 FROM user_daily_active a
                       WHERE a.user_id = s.user_id
                         AND a.active_date >  s.send_date
                         AND a.active_date <= s.send_date + :returnWindowDays
                   ))                                        AS "returned"
            FROM reengagement_sends s
            WHERE s.send_date >= :sinceDate
            GROUP BY s.nudge_type
            ORDER BY s.nudge_type
        """,
        nativeQuery = true,
    )
    fun reengagementReturnByType(
        @Param("sinceDate") sinceDate: LocalDate,
        @Param("returnWindowDays") returnWindowDays: Int,
    ): List<ReengagementRow>

    /** Opt-out guardrail: how many users disabled re-engagement alerts vs total. */
    @Query(
        value = """
            SELECT COUNT(*) FILTER (WHERE NOT is_reengagement_alert_enabled) AS "optedOut",
                   COUNT(*)                                                  AS "total"
            FROM users
        """,
        nativeQuery = true,
    )
    fun reengagementOptOut(): ReengagementOptOutRow

    @Query(value = "SELECT TO_CHAR(MIN(send_date), 'YYYY-MM-DD') FROM reengagement_sends", nativeQuery = true)
    fun reengagementDataAvailableSince(): String?

    // ------------------------------------------------------------------
    // 4. boost — profile_boosts count + gold spent in window
    // ------------------------------------------------------------------
    @Query(
        value = """
            SELECT COUNT(*)                       AS "boostCount",
                   COALESCE(SUM(gold_cost), 0)    AS "goldSpent",
                   COUNT(DISTINCT user_id)        AS "uniqueUsers"
            FROM profile_boosts
            WHERE started_at >= :since
        """,
        nativeQuery = true,
    )
    fun boostSummary(@Param("since") since: LocalDateTime): BoostSummaryRow

    @Query(value = "SELECT TO_CHAR(MIN(started_at), 'YYYY-MM-DD') FROM profile_boosts", nativeQuery = true)
    fun boostDataAvailableSince(): String?

    // ------------------------------------------------------------------
    // 5. retention / DAU — user_daily_active
    // ------------------------------------------------------------------
    @Query(
        value = """
            SELECT TO_CHAR(active_date, 'YYYY-MM-DD') AS "date",
                   COUNT(DISTINCT user_id)            AS "dau"
            FROM user_daily_active
            WHERE active_date >= :sinceDate
            GROUP BY active_date
            ORDER BY active_date
        """,
        nativeQuery = true,
    )
    fun dauByDay(@Param("sinceDate") sinceDate: LocalDate): List<DauRow>

    /** Signup-cohort D1/D7: users.created_at::date cohort active on day N after signup. */
    // u.created_at (TIMESTAMP) → KST calendar day so the signup-cohort boundary matches the
    // KST user_daily_active.active_date (avoids a TZ-skewed off-by-one vs the DB session TZ).
    @Query(
        value = """
            SELECT TO_CHAR((u.created_at AT TIME ZONE 'Asia/Seoul')::date, 'YYYY-MM-DD') AS "cohortDate",
                   COUNT(DISTINCT u.id)                      AS "cohortSize",
                   COUNT(DISTINCT a1.user_id)                AS "d1Retained",
                   COUNT(DISTINCT a7.user_id)                AS "d7Retained"
            FROM users u
            LEFT JOIN user_daily_active a1
              ON a1.user_id = u.id AND a1.active_date = (u.created_at AT TIME ZONE 'Asia/Seoul')::date + 1
            LEFT JOIN user_daily_active a7
              ON a7.user_id = u.id AND a7.active_date = (u.created_at AT TIME ZONE 'Asia/Seoul')::date + 7
            WHERE (u.created_at AT TIME ZONE 'Asia/Seoul')::date >= :sinceDate
            GROUP BY (u.created_at AT TIME ZONE 'Asia/Seoul')::date
            ORDER BY (u.created_at AT TIME ZONE 'Asia/Seoul')::date
        """,
        nativeQuery = true,
    )
    fun signupCohortRetention(@Param("sinceDate") sinceDate: LocalDate): List<RetentionCohortRow>

    @Query(value = "SELECT TO_CHAR(MIN(active_date), 'YYYY-MM-DD') FROM user_daily_active", nativeQuery = true)
    fun dauDataAvailableSince(): String?

    // ------------------------------------------------------------------
    // 6. streak correlation (CORRELATIONAL, NOT causal)
    // ------------------------------------------------------------------
    // D7 retention of signup-cohort users WITH ≥1 streak day vs WITHOUT. Selection-biased:
    // users self-select into walking, so this is NOT a controlled A/B comparison.
    @Query(
        value = """
            SELECT (us.user_id IS NOT NULL)           AS "hasStreak",
                   COUNT(DISTINCT u.id)               AS "users",
                   COUNT(DISTINCT a7.user_id)         AS "d7Active"
            FROM users u
            LEFT JOIN user_streaks us
              ON us.user_id = u.id AND us.current_streak >= 1
            LEFT JOIN user_daily_active a7
              ON a7.user_id = u.id AND a7.active_date = (u.created_at AT TIME ZONE 'Asia/Seoul')::date + 7
            WHERE (u.created_at AT TIME ZONE 'Asia/Seoul')::date >= :sinceDate
              AND (u.created_at AT TIME ZONE 'Asia/Seoul')::date <= :matureCutoff
            GROUP BY (us.user_id IS NOT NULL)
        """,
        nativeQuery = true,
    )
    fun streakD7Correlation(
        @Param("sinceDate") sinceDate: LocalDate,
        @Param("matureCutoff") matureCutoff: LocalDate,
    ): List<StreakCorrelationRow>
}

// ----------------------------------------------------------------------
// Projection interfaces (column labels double-quoted in SQL to match getters)
// ----------------------------------------------------------------------

interface CohortConversionRow {
    val cohort: String
    val likeActions: Long
    val matches: Long
}

interface MissionCompletionRow {
    val date: String
    val missionsOffered: Long?
    val completions: Long
    val uniqueCompleters: Long
}

interface ReengagementRow {
    val nudgeType: String
    val sends: Long
    val returned: Long
}

interface ReengagementOptOutRow {
    val optedOut: Long
    val total: Long
}

interface BoostSummaryRow {
    val boostCount: Long
    val goldSpent: Long
    val uniqueUsers: Long
}

interface DauRow {
    val date: String
    val dau: Long
}

interface RetentionCohortRow {
    val cohortDate: String
    val cohortSize: Long
    val d1Retained: Long
    val d7Retained: Long
}

interface StreakCorrelationRow {
    val hasStreak: Boolean
    val users: Long
    val d7Active: Long
}
