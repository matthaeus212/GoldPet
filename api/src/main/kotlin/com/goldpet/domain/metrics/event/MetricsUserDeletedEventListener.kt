package com.goldpet.domain.metrics.event

import com.goldpet.domain.experiment.repository.ExperimentAssignmentRepository
import com.goldpet.domain.metrics.repository.LikeEventRepository
import com.goldpet.domain.metrics.repository.UserDailyActiveRepository
import com.goldpet.domain.user.event.UserDeletedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Purges a withdrawn user's metrics rows on deletion.
 *
 * The deletion path ([com.goldpet.domain.user.service.UserService.anonymizeAndDelete])
 * anonymizes the `users` row and sets status=WITHDRAWN — it never hard-deletes it — so
 * the "ON DELETE CASCADE" FKs on the metrics tables never fire. Without this listener the
 * user's experiment_assignment / user_daily_active / like_events rows (including the
 * like_events.target_user_id graph edges) would survive indefinitely → GDPR erasure gap.
 *
 * Uses plain @EventListener + @Transactional, mirroring Chat/CommunityUserDeletedEventListener:
 * UserDeletedEvent is published synchronously inside UserService.anonymizeAndDelete's @Transactional
 * method, so this listener runs in that same transaction (the purge commits/rolls back with the deletion).
 */
@Component
class MetricsUserDeletedEventListener(
    private val likeEventRepository: LikeEventRepository,
    private val userDailyActiveRepository: UserDailyActiveRepository,
    private val experimentAssignmentRepository: ExperimentAssignmentRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Transactional
    fun handleUserDeleted(event: UserDeletedEvent) {
        val userId = event.userId
        val likeEvents = likeEventRepository.deleteByUserParticipation(userId)
        val dailyActive = userDailyActiveRepository.deleteByUserId(userId)
        val experiments = experimentAssignmentRepository.deleteByUserId(userId)
        log.info(
            "Purged metrics for withdrawn user {}: like_events={}, user_daily_active={}, experiment_assignment={}",
            userId, likeEvents, dailyActive, experiments,
        )
    }
}
