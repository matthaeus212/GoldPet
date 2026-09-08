package com.goldpet.domain.metrics.service

import com.goldpet.domain.metrics.repository.UserDailyActiveRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

/**
 * Records the app-open DAU signal. The write is fully asynchronous so it never
 * blocks the originating request, and is idempotent per user per KST day.
 */
@Service
class DailyActiveService(
    private val userDailyActiveRepository: UserDailyActiveRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Marks [userId] active for "today" in KST, off the request thread.
     * Runs in its own transaction (the insert is independent of any caller tx),
     * and swallows failures so metrics collection can never break a user flow.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordActiveAsync(userId: Long) {
        try {
            userDailyActiveRepository.insertIfAbsent(userId, LocalDate.now(KST))
        } catch (e: Exception) {
            log.warn("Failed to record daily-active for user {}: {}", userId, e.message)
        }
    }

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
