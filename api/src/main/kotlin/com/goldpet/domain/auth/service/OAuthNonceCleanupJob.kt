package com.goldpet.domain.auth.service

import com.goldpet.domain.auth.repository.OAuthNonceRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * V67: 만료된 oauth_nonces 일별 정리.
 * - 04:40 KST — GoldIdempotency 04:30 / RefreshToken 04:35 다음 5분 간격 (DB 부하 분산).
 * - 5분 TTL 초과 nonces 삭제 (used 여부 무관 — replay 차단이 시간 기반이므로 stale row 보존 가치 없음).
 */
@Component
class OAuthNonceCleanupJob(
    private val repo: OAuthNonceRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 40 4 * * *")
    @SchedulerLock(name = "oauthNonceCleanup", lockAtMostFor = "5m")
    @Transactional
    fun cleanupExpired() {
        val deleted = repo.deleteExpired(LocalDateTime.now())
        if (deleted > 0) {
            log.info("Deleted {} expired oauth nonces", deleted)
        }
    }
}
