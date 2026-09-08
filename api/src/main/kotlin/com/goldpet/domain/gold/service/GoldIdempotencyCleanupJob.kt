package com.goldpet.domain.gold.service

import com.goldpet.domain.gold.repository.GoldIdempotencyKeyRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 만료된 idempotency record 일별 정리.
 * - 04:30 KST 매일 실행 (UserDataRetentionService 03:00/04:00 SUN과 시간 충돌 회피).
 * - 7일 TTL 초과 record 삭제 (V65 기본 expires_at = created_at + 7d).
 */
@Component
class GoldIdempotencyCleanupJob(
    private val repo: GoldIdempotencyKeyRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 4 * * *")
    @SchedulerLock(name = "goldIdempotencyCleanup", lockAtMostFor = "10m")
    @Transactional
    fun cleanupExpired() {
        val deleted = repo.deleteExpired(LocalDateTime.now())
        if (deleted > 0) {
            log.info("Deleted {} expired gold idempotency keys", deleted)
        }
    }
}
