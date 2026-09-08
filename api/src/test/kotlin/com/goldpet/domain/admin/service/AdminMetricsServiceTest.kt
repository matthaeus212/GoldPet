package com.goldpet.domain.admin.service

import com.goldpet.IntegrationTestBase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId

/**
 * Verifies every AdminMetrics native-SQL aggregation against hand-seeded data.
 *
 * Isolation strategy: the pure append-only capture/log tables (like_events, user_daily_active,
 * profile_boosts, reengagement_sends, daily_mission_set) are truncated per test so seeded counts
 * are exact. Globally-scoped tables (users, user_badges, user_streaks) are not truncated — seeded
 * rows are deleted in @AfterEach and assertions on global counts use ≥, while activity-derived
 * counts (DAU/return/D7) stay exact because they read only the truncated capture tables.
 */
@Tag("integration")
class AdminMetricsServiceTest : IntegrationTestBase() {

    @Autowired private lateinit var service: AdminMetricsService
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val kst = ZoneId.of("Asia/Seoul")
    private val today: LocalDate get() = LocalDate.now(kst)
    private val seededUserIds = mutableListOf<Long>()
    private val seededCycleKeys = mutableListOf<String>()

    @BeforeEach
    fun truncateCaptureTables() {
        jdbcTemplate.execute(
            "TRUNCATE like_events, user_daily_active, profile_boosts, reengagement_sends, daily_mission_set RESTART IDENTITY"
        )
    }

    @AfterEach
    fun cleanup() {
        if (seededCycleKeys.isNotEmpty()) {
            jdbcTemplate.update(
                "DELETE FROM user_badges WHERE cycle_key IN (${seededCycleKeys.joinToString(",") { "?" }})",
                *seededCycleKeys.toTypedArray()
            )
        }
        if (seededUserIds.isNotEmpty()) {
            val placeholders = seededUserIds.joinToString(",") { "?" }
            val ids = seededUserIds.toTypedArray()
            jdbcTemplate.update("DELETE FROM user_badges WHERE user_id IN ($placeholders)", *ids)
            jdbcTemplate.update("DELETE FROM user_streaks WHERE user_id IN ($placeholders)", *ids)
            jdbcTemplate.update("DELETE FROM users WHERE id IN ($placeholders)", *ids)
        }
        jdbcTemplate.execute(
            "TRUNCATE like_events, user_daily_active, profile_boosts, reengagement_sends, daily_mission_set RESTART IDENTITY"
        )
        seededUserIds.clear()
        seededCycleKeys.clear()
    }

    private fun insertUser(createdAt: LocalDate, reengageEnabled: Boolean = true): Long {
        val ts = System.nanoTime()
        val id = jdbcTemplate.queryForObject(
            """
            INSERT INTO users (email, created_at, updated_at, is_reengagement_alert_enabled)
            VALUES (?, ?, ?, ?) RETURNING id
            """.trimIndent(),
            Long::class.java,
            "metrics_${ts}_${seededUserIds.size}@test.goldpet",
            Timestamp.valueOf(createdAt.atTime(12, 0)),
            Timestamp.valueOf(createdAt.atTime(12, 0)),
            reengageEnabled,
        )!!
        seededUserIds.add(id)
        return id
    }

    private fun insertLike(userId: Long, action: String, isMatch: Boolean, cohort: String) {
        jdbcTemplate.update(
            "INSERT INTO like_events (user_id, target_user_id, action, is_match, cohort, source, occurred_at) VALUES (?,?,?,?,?,?,?)",
            userId, 999L, action, isMatch, cohort, "compatible", Timestamp.valueOf(today.atTime(12, 0)),
        )
    }

    private fun insertActive(userId: Long, date: LocalDate) {
        jdbcTemplate.update(
            "INSERT INTO user_daily_active (user_id, active_date) VALUES (?, ?) ON CONFLICT DO NOTHING",
            userId, Date.valueOf(date),
        )
    }

    // ------------------------------------------------------------------
    @Test
    fun `match-rate aggregates LIKE funnel by cohort, excludes NONE from A_B, exposes noneFraction`() {
        val u = insertUser(today)
        // TREATMENT: 4 LIKE (2 match) + 1 CANCEL (ignored)
        insertLike(u, "LIKE", true, "TREATMENT")
        insertLike(u, "LIKE", true, "TREATMENT")
        insertLike(u, "LIKE", false, "TREATMENT")
        insertLike(u, "LIKE", false, "TREATMENT")
        insertLike(u, "CANCEL", false, "TREATMENT")
        // CONTROL: 2 LIKE (0 match)
        insertLike(u, "LIKE", false, "CONTROL")
        insertLike(u, "LIKE", false, "CONTROL")
        // NONE: 3 LIKE (1 match) — excluded from A/B, counted in noneFraction
        insertLike(u, "LIKE", true, "NONE")
        insertLike(u, "LIKE", false, "NONE")
        insertLike(u, "LIKE", false, "NONE")

        val r = service.getMatchRate(30)

        assertEquals(4L, r.treatment.likeActions)
        assertEquals(2L, r.treatment.matches)
        assertEquals(0.5, r.treatment.matchRate)
        assertEquals(2L, r.control.likeActions)
        assertEquals(0L, r.control.matches)
        assertEquals(0.0, r.control.matchRate)
        // noneFraction = 3 / (4 + 2 + 3) = 0.333…
        assertEquals(3.0 / 9.0, r.noneFraction, 1e-9)
        assertEquals(today, r.window.dataAvailableSince)
    }

    @Test
    fun `mission completion counts DAILY user_badges in the day's badge set`() {
        val missionDate = today.minusDays(3)
        val cycleKey = missionDate.toString()
        seededCycleKeys.add(cycleKey)
        // Ensure isolation from any pre-existing badges on this cycle key.
        jdbcTemplate.update("DELETE FROM user_badges WHERE cycle_key = ?", cycleKey)

        val badgeIds = jdbcTemplate.queryForList("SELECT id FROM badges ORDER BY id LIMIT 2", Long::class.java)
        assertTrue(badgeIds.size >= 2, "test DB must seed ≥2 badges")
        val (b1, b2) = badgeIds[0] to badgeIds[1]
        val outsider = jdbcTemplate.queryForList(
            "SELECT id FROM badges WHERE id NOT IN (?, ?) ORDER BY id LIMIT 1", Long::class.java, b1, b2,
        ).firstOrNull()

        jdbcTemplate.update(
            "INSERT INTO daily_mission_set (mission_date, badge_ids) VALUES (?, ?::jsonb)",
            Date.valueOf(missionDate), "[$b1, $b2]",
        )

        val ua = insertUser(today)
        val ub = insertUser(today)
        // ua completes b1 and b2; ub completes b1 → 3 completions, 2 unique completers.
        insertUserBadge(ua, b1, cycleKey)
        insertUserBadge(ua, b2, cycleKey)
        insertUserBadge(ub, b1, cycleKey)
        // Noise: badge outside the set, and wrong cycle_key → excluded.
        if (outsider != null) insertUserBadge(ua, outsider, cycleKey)
        insertUserBadge(ub, b2, today.minusDays(4).toString().also { seededCycleKeys.add(it) })

        val r = service.getMissionCompletion(30)
        val row = r.days.firstOrNull { it.date == missionDate }
        assertNotNull(row, "expected a row for $missionDate")
        assertEquals(2L, row!!.missionsOffered)
        assertEquals(3L, row.completions)
        assertEquals(2L, row.uniqueCompleters)
    }

    private fun insertUserBadge(userId: Long, badgeId: Long, cycleKey: String) {
        jdbcTemplate.update(
            "INSERT INTO user_badges (user_id, badge_id, cycle_key) VALUES (?, ?, ?)",
            userId, badgeId, cycleKey,
        )
    }

    @Test
    fun `re-engagement return-rate joins active within window and reports opt-out guardrail`() {
        val a = insertUser(today)
        val b = insertUser(today)
        val c = insertUser(today)
        val optedOut = insertUser(today, reengageEnabled = false)

        insertSend(a, today.minusDays(5), "DORMANCY")
        insertActive(a, today.minusDays(3)) // within send+7 → returned
        insertSend(b, today.minusDays(5), "DORMANCY") // no active → not returned
        insertSend(c, today.minusDays(10), "STREAK_AT_RISK")
        insertActive(c, today.minusDays(2)) // send+7 = today-3; today-2 is AFTER → not returned

        val r = service.getReengagement(30, 7)

        val dormancy = r.byType.first { it.nudgeType == "DORMANCY" }
        assertEquals(2L, dormancy.sends)
        assertEquals(1L, dormancy.returned)
        assertEquals(0.5, dormancy.returnRate)

        val risk = r.byType.first { it.nudgeType == "STREAK_AT_RISK" }
        assertEquals(1L, risk.sends)
        assertEquals(0L, risk.returned)

        assertEquals(3L, r.totalSends)
        assertEquals(1L, r.totalReturned)
        // opt-out is global; assert our opted-out user is counted.
        assertTrue(r.optedOutUsers >= 1L)
        assertTrue(r.totalUsers >= 4L)
        assertTrue(r.optOutFraction in 0.0..1.0)
        assertTrue(r.note.contains("one nudge_type per user per day"))
    }

    private fun insertSend(userId: Long, sendDate: LocalDate, nudgeType: String) {
        jdbcTemplate.update(
            "INSERT INTO reengagement_sends (user_id, send_date, nudge_type) VALUES (?, ?, ?)",
            userId, Date.valueOf(sendDate), nudgeType,
        )
    }

    @Test
    fun `boost summary counts purchases and sums gold spent`() {
        val u1 = insertUser(today)
        val u2 = insertUser(today)
        insertBoost(u1, 100)
        insertBoost(u1, 100)
        insertBoost(u2, 200)

        val r = service.getBoost(30)
        assertEquals(3L, r.boostCount)
        assertEquals(400L, r.goldSpent)
        assertEquals(2L, r.uniqueUsers)
    }

    private fun insertBoost(userId: Long, goldCost: Int) {
        jdbcTemplate.update(
            "INSERT INTO profile_boosts (user_id, started_at, expires_at, gold_cost) VALUES (?, ?, ?, ?)",
            userId, Timestamp.valueOf(today.atTime(12, 0)), Timestamp.valueOf(today.atTime(13, 0)), goldCost,
        )
    }

    @Test
    fun `retention computes DAU and signup-cohort D1_D7 with maturity flags`() {
        val cohortDate = today.minusDays(8)
        val users = (1..3).map { insertUser(cohortDate) }
        // D1 (cohort+1 = today-7): 2 active. D7 (cohort+7 = today-1): 1 active.
        insertActive(users[0], today.minusDays(7))
        insertActive(users[1], today.minusDays(7))
        insertActive(users[0], today.minusDays(1))

        val r = service.getRetention(30)

        val cohort = r.cohorts.first { it.cohortDate == cohortDate }
        assertTrue(cohort.cohortSize >= 3L)
        assertEquals(2L, cohort.d1Retained)
        assertEquals(1L, cohort.d7Retained)
        assertTrue(cohort.d1Mature)
        assertTrue(cohort.d7Mature)

        // DAU is exact (only seeded rows exist in the truncated table).
        assertEquals(2L, r.dau.first { it.date == today.minusDays(7) }.dau)
        assertEquals(1L, r.dau.first { it.date == today.minusDays(1) }.dau)
        assertEquals(today.minusDays(7), r.window.dataAvailableSince)
    }

    @Test
    fun `streak correlation splits D7 by streak presence and labels correlational`() {
        val cohortDate = today.minusDays(10)
        val day7 = today.minusDays(3) // cohort+7

        val withStreak = (1..2).map { insertUser(cohortDate) }
        withStreak.forEach { insertStreak(it) }
        insertActive(withStreak[0], day7) // 1 of 2 active on D7

        val withoutStreak = (1..2).map { insertUser(cohortDate) }
        insertActive(withoutStreak[0], day7) // 1 of 2 active on D7

        val r = service.getStreakCorrelation(60)

        // d7Active is exact (truncated user_daily_active); user counts are global → ≥.
        assertEquals(1L, r.withStreak.d7Active)
        assertEquals(1L, r.withoutStreak.d7Active)
        assertTrue(r.withStreak.users >= 2L)
        assertTrue(r.withoutStreak.users >= 2L)
        assertTrue(r.note.contains("correlational, selection-biased"))
        assertTrue(r.note.contains("as of query time, not as of the retention window"))
    }

    private fun insertStreak(userId: Long) {
        jdbcTemplate.update(
            "INSERT INTO user_streaks (user_id, current_streak, longest_streak, last_active_date, freeze_count) VALUES (?, ?, ?, ?, ?)",
            userId, 3, 5, Date.valueOf(today), 1,
        )
    }
}
