package com.goldpet.domain.auth.service

import com.goldpet.domain.auth.repository.UserRefreshTokenRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * V66: 만료된 user_refresh_tokens 일별 정리.
 * - 04:35 KST 매일 — GoldIdempotencyCleanupJob 04:30 다음 5분 간격 (DB 부하 분산).
 * - 30일 TTL (JwtTokenProvider refresh-expiration-ms 기본 2592000000ms = 30d) 초과 token 삭제.
 */
@Component
class RefreshTokenCleanupJob(
    private val repo: UserRefreshTokenRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 35 4 * * *")
    @SchedulerLock(name = "refreshTokenCleanup", lockAtMostFor = "5m")
    @Transactional
    fun cleanupExpired() {
        val deleted = repo.deleteExpired(LocalDateTime.now())
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted)
        }
    }
}
