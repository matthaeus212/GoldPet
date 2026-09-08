package com.goldpet.domain.health.service

import com.goldpet.domain.walk.entity.AnalysisStatus
import com.goldpet.domain.walk.repository.StoolAnalysisRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class StoolAnalysisCleanupJob(
    private val stoolAnalysisRepository: StoolAnalysisRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 300_000)
    @SchedulerLock(name = "stoolAnalysisCleanup", lockAtMostFor = "4m", lockAtLeastFor = "30s")
    @Transactional
    fun markStuckAnalysesFailed() {
        val threshold = LocalDateTime.now().minusMinutes(5)
        val stuck = stoolAnalysisRepository.findByStatusAndCreatedAtBefore(AnalysisStatus.ANALYZING, threshold)
        if (stuck.isEmpty()) return

        stuck.forEach { analysis ->
            analysis.status = AnalysisStatus.FAILED
            analysis.errorMessage = "분석 시간 초과 (5분)"
        }
        stoolAnalysisRepository.saveAll(stuck)
        log.warn("Marked {} stuck analyses as FAILED", stuck.size)
    }
}
