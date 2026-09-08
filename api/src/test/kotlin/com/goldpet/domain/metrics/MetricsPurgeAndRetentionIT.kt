package com.goldpet.domain.metrics

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.experiment.repository.ExperimentAssignmentRepository
import com.goldpet.domain.metrics.repository.UserDailyActiveRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

/**
 * Covers the FIX-B delete predicates owned by this worker:
 *  - GDPR purge: UserDailyActiveRepository.deleteByUserId + ExperimentAssignmentRepository.deleteByUserId
 *  - TTL retention: UserDailyActiveRepository.deleteByActiveDateBefore
 *
 * (like_events.deleteByUserParticipation / deleteByOccurredAtBefore are owned + tested by
 * worker-likeevents; MetricsUserDeletedEventListener / MetricsRetentionJob call them by signature.)
 *
 * NOT @Transactional — IntegrationTestBase drains the Hikari pool in a @BeforeEach, which
 * deadlocks if a test transaction holds a connection; each repo call commits and we clean
 * up explicitly (same pattern as IdempotencyConcurrencyIT / UserDailyActiveRepositoryIT).
 */
class MetricsPurgeAndRetentionIT : IntegrationTestBase() {

    @Autowired private lateinit var userDailyActiveRepository: UserDailyActiveRepository
    @Autowired private lateinit var experimentAssignmentRepository: ExperimentAssignmentRepository
    @Autowired private lateinit var userRepository: UserRepository

    private lateinit var subject: User

    @BeforeEach
    fun setUp() {
        subject = userRepository.findAll().firstOrNull()
            ?: error("Test DB must seed at least one user — run a seed migration or DB-init script first")
        cleanup()
    }

    @AfterEach
    fun tearDown() = cleanup()

    private fun cleanup() {
        userDailyActiveRepository.deleteByUserId(subject.id)
        experimentAssignmentRepository.deleteByUserId(subject.id)
    }

    @Test
    fun `GDPR purge removes the withdrawn user's daily-active and experiment rows`() {
        userDailyActiveRepository.insertIfAbsent(subject.id, LocalDate.now())
        experimentAssignmentRepository.insertIfAbsent(subject.id, "purge_test_exp", "CONTROL", 50, 1)

        assertEquals(1L, userDailyActiveRepository.countByUserId(subject.id), "precondition")

        userDailyActiveRepository.deleteByUserId(subject.id)
        experimentAssignmentRepository.deleteByUserId(subject.id)

        assertEquals(0L, userDailyActiveRepository.countByUserId(subject.id))
        assertNull(experimentAssignmentRepository.findByUserIdAndExperimentKey(subject.id, "purge_test_exp"))
    }

    @Test
    fun `TTL retention deletes only daily-active rows older than the 13-month cutoff`() {
        val today = LocalDate.now()
        userDailyActiveRepository.insertIfAbsent(subject.id, today.minusMonths(14))
        userDailyActiveRepository.insertIfAbsent(subject.id, today.minusMonths(1))

        userDailyActiveRepository.deleteByActiveDateBefore(today.minusMonths(13))

        assertEquals(1L, userDailyActiveRepository.countByUserId(subject.id), "only the recent row should remain")
    }
}
