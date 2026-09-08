package com.goldpet.domain.walk.service

import com.goldpet.domain.common.repository.FileAttachmentRepository
import com.goldpet.domain.walk.repository.WalkSpotRepository
import io.micrometer.core.instrument.MeterRegistry
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Reconciles walk_spots whose variants weren't written at walk-create time
 * (async variant-gen still in flight or rejected by the executor queue).
 *
 * Tick = 5 minutes. Each pass:
 *  1) copies variant keys from matured FileAttachment rows into the spot
 *  2) falls back to a full backfill (S3 fetch + variant gen) for spots older than 5 min
 */
@Component
class WalkSpotVariantSyncScheduler(
    private val walkSpotRepository: WalkSpotRepository,
    private val fileAttachmentRepository: FileAttachmentRepository,
    private val walkSpotBackfillWorker: WalkSpotBackfillWorker,
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 300_000)
    @SchedulerLock(name = "walkSpotVariantSync", lockAtMostFor = "4m", lockAtLeastFor = "30s")
    fun sweepMissingVariants() {
        val reconciledFromAttachments = reconcile(batchSize = 100)
        val backfilled = backfillStragglers(batchSize = 50)
        if (reconciledFromAttachments > 0 || backfilled > 0) {
            log.info(
                "WalkSpotVariantSync tick: reconciled={} backfilled={}",
                reconciledFromAttachments, backfilled
            )
        }
    }

    @Transactional
    fun reconcile(batchSize: Int): Int {
        val spots = walkSpotRepository.findSpotsForSweep(
            before = LocalDateTime.now().minusMinutes(1),
            pageable = PageRequest.of(0, batchSize)
        )
        var reconciled = 0
        for (spot in spots) {
            val url = spot.imageUrl ?: continue
            val attachment = try {
                fileAttachmentRepository.findByUrl(url)
            } catch (e: Exception) {
                log.warn("reconcile findByUrl failed for spotId={}: {}", spot.id, e.message)
                null
            } ?: continue
            val viewer = attachment.viewerUrl
            val thumb = attachment.thumbnailUrl
            // 변형 생성 실패 첨부는 mediumUrl 이 원본 키로 대체(degraded)돼 있으므로 실제 `_medium` 변형만 인정.
            // 아니면 null 로 남겨 backfillStragglers 가 생성하도록 한다.
            val medium = attachment.mediumUrl?.takeIf { it.contains("_medium") }
            if (viewer != null) spot.imageKeyViewer = viewer
            if (thumb != null) spot.imageKeyThumb = thumb
            if (medium != null) spot.imageKeyMedium = medium
            if (viewer != null || thumb != null || medium != null) {
                reconciled++
                meterRegistry.counter(
                    "walk_photo_variant_dlq_sweep_total",
                    "outcome", "reconciled"
                ).increment()
            }
        }
        return reconciled
    }

    fun backfillStragglers(batchSize: Int): Int {
        val spots = walkSpotRepository.findSpotsForSweep(
            before = LocalDateTime.now().minusMinutes(5),
            pageable = PageRequest.of(0, batchSize)
        )
        var processed = 0
        for (spot in spots) {
            val result = walkSpotBackfillWorker.processRow(spot.id, dryRun = false)
            val outcomeTag = when (result.outcome) {
                WalkSpotBackfillWorker.ProcessResult.Outcome.PROCESSED -> {
                    processed++
                    "processed"
                }
                WalkSpotBackfillWorker.ProcessResult.Outcome.SKIPPED -> "skipped"
                WalkSpotBackfillWorker.ProcessResult.Outcome.FAILED -> "failed"
                WalkSpotBackfillWorker.ProcessResult.Outcome.NOT_FOUND -> "not_found"
            }
            meterRegistry.counter(
                "walk_photo_variant_dlq_sweep_total",
                "outcome", outcomeTag
            ).increment()
        }
        return processed
    }
}
