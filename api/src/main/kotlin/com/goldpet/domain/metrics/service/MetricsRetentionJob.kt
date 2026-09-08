package com.goldpet.domain.metrics.service

import com.goldpet.domain.metrics.repository.LikeEventRepository
import com.goldpet.domain.metrics.repository.UserDailyActiveRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Nightly TTL retention for the metrics tables (~13 months), mirroring
 * RefreshTokenCleanupJob / GoldIdempotencyCleanupJob.
 *
 * - like_events: pruned by occurred_at (event timestamp).
 * - user_daily_active: pruned by active_date.
 * - experiment_assignment: intentionally NOT time-pruned. Its cardinality is bounded
 *   (one row per user per experiment — not a growing time series), and it is erased on
 *   user withdrawal by [com.goldpet.domain.metrics.event.MetricsUserDeletedEventListener].
 */
@Component
class MetricsRetentionJob(
    private val likeEventRepository: LikeEventRepository,
    private val userDailyActiveRepository: UserDailyActiveRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Runs daily at 04:30 KST (offset from the 04:00 refresh-token job). */
    @Scheduled(cron = "0 30 4 * * *", zone = ZONE)
    @SchedulerLock(name = "metricsRetention", lockAtMostFor = "10m")
    @Transactional
    fun purgeExpiredMetrics() {
        val likeCutoff = LocalDateTime.now(KST).minusMonths(RETENTION_MONTHS)
        val dailyCutoff = LocalDate.now(KST).minusMonths(RETENTION_MONTHS)

        val likeEvents = likeEventRepository.deleteByOccurredAtBefore(likeCutoff)
        val dailyActive = userDailyActiveRepository.deleteByActiveDateBefore(dailyCutoff)

        log.info(
            "Metrics retention: deleted {} like_events (occurred_at < {}) and {} user_daily_active (active_date < {})",
            likeEvents, likeCutoff, dailyActive, dailyCutoff,
        )
    }

    companion object {
        const val ZONE = "Asia/Seoul"
        const val RETENTION_MONTHS = 13L
        private val KST: ZoneId = ZoneId.of(ZONE)
    }
}
