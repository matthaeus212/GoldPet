// 이미지 변형(thumb/medium/viewer) 백필 배치를 담당하는 서비스 — 업로드/스토리지 핵심(FileService)과 분리
package com.goldpet.domain.file.service

import com.amazonaws.services.s3.AmazonS3
import com.goldpet.domain.common.repository.FileAttachmentRepository
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.walk.service.WalkSpotBackfillWorker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import javax.imageio.ImageIO

/**
 * ARCH-004: 원래 FileService(837행) 안에 업로드·스토리지·프리사인 핵심과 뒤섞여 있던 백필 배치들을
 * 분리한다. 백필은 운영 배치(admin 엔드포인트/스케줄러)로만 호출되는 별개의 관심사다.
 *
 * 부수 효과로 `FileService ⇄ FileAttachmentRetryWorker` 의 양방향 `@Lazy` 순환이 사라진다.
 * 순환의 원인은 FileService 가 백필을 하느라 워커를 주입받은 것이었고, 워커는 변형 생성을 위해
 * FileService 를 필요로 했다. 이제 의존이 한 방향으로 흐른다:
 *   FileBackfillService → (FileService, 워커들) → …
 */
@Service
class FileBackfillService(
    private val amazonS3: AmazonS3,
    @Value("\${S3_PUBLIC_BUCKET_NAME:goldpet-public}") private val publicBucketName: String,
    @Value("\${S3_PUBLIC_ENDPOINT:http://localhost:9100}") private val publicEndpoint: String,
    private val fileAttachmentRepository: FileAttachmentRepository,
    private val fileService: FileService,
    private val walkSpotBackfillWorker: WalkSpotBackfillWorker,
    private val fileAttachmentRetryWorker: FileAttachmentRetryWorker,
    private val systemSettingService: SystemSettingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class BackfillResult(
        val total: Long,
        val processed: Int,
        val skipped: Int,
        val failed: Int,
        val durationMs: Long
    )

    enum class BackfillScope { ALL, FILE_ATTACHMENTS, FILE_ATTACHMENTS_RETRY, WALK_SPOTS }

    companion object {
        const val RETRY_ENABLED_KEY = "image.backfill.retry_enabled"
        private const val RETRY_COOLDOWN_DAYS = 7L
    }

    /**
     * Scoped backfill. Per-row work executes through `WalkSpotBackfillWorker` (REQUIRES_NEW)
     * so pod restarts mid-batch leave committed rows intact.
     * NO @Transactional on this outer method — the worker beans own their own tx.
     */
    fun backfillVariants(scope: BackfillScope, batchSize: Int, dryRun: Boolean = false): BackfillResult {
        return when (scope) {
            BackfillScope.FILE_ATTACHMENTS -> backfillFileAttachments(batchSize, dryRun)
            BackfillScope.FILE_ATTACHMENTS_RETRY -> backfillFileAttachmentsRetry(batchSize, dryRun)
            BackfillScope.WALK_SPOTS -> backfillWalkSpots(batchSize, dryRun)
            BackfillScope.ALL -> {
                val a = backfillFileAttachments(batchSize, dryRun)
                val b = backfillWalkSpots(batchSize, dryRun)
                BackfillResult(
                    total = a.total + b.total,
                    processed = a.processed + b.processed,
                    skipped = a.skipped + b.skipped,
                    failed = a.failed + b.failed,
                    durationMs = a.durationMs + b.durationMs
                )
            }
        }
    }

    fun backfillVariants(batchSize: Int): BackfillResult = backfillFileAttachments(batchSize)

    /**
     * T0-6 retry sweep. Only runs when `image.backfill.retry_enabled` system setting is true
     * (default true; admin /stop endpoint flips it to false). Targets rows where original
     * backfill skip-marked (thumb=url / medium=url / viewer NULL w/ thumb set), cooled off
     * by 7 days so permanent failures don't loop.
     */
    fun backfillFileAttachmentsRetry(batchSize: Int, dryRun: Boolean): BackfillResult {
        val startMs = System.currentTimeMillis()
        val enabled = systemSettingService.getBoolean(RETRY_ENABLED_KEY, true)
        if (!enabled) {
            log.info("backfillFileAttachmentsRetry skipped: {}=false", RETRY_ENABLED_KEY)
            return BackfillResult(total = 0, processed = 0, skipped = 0, failed = 0, durationMs = 0)
        }
        val cutoff = LocalDateTime.now().minusDays(RETRY_COOLDOWN_DAYS)
        val page = fileAttachmentRepository.findImagesForRetry(cutoff, PageRequest.of(0, batchSize))
        var processed = 0
        var skipped = 0
        var failed = 0
        for (fa in page.content) {
            when (fileAttachmentRetryWorker.processRow(fa.id, dryRun)) {
                FileAttachmentRetryWorker.Outcome.PROCESSED -> processed++
                FileAttachmentRetryWorker.Outcome.SKIPPED,
                FileAttachmentRetryWorker.Outcome.NOT_FOUND -> skipped++
                FileAttachmentRetryWorker.Outcome.FAILED -> failed++
            }
        }
        return BackfillResult(
            total = page.totalElements,
            processed = processed,
            skipped = skipped,
            failed = failed,
            durationMs = System.currentTimeMillis() - startMs
        )
    }

    /** Admin kill switch: flips the retry feature flag off. Next sweep tick will short-circuit. */
    fun stopBackfillRetry() {
        systemSettingService.setValue(
            RETRY_ENABLED_KEY,
            "false",
            description = "T0-6 retry sweep kill switch (set to true to resume)"
        )
    }

    private fun backfillWalkSpots(batchSize: Int, dryRun: Boolean): BackfillResult {
        val startMs = System.currentTimeMillis()
        val result = walkSpotBackfillWorker.backfillBatch(batchSize, dryRun)
        return BackfillResult(
            total = result.total,
            processed = result.processed,
            skipped = result.skipped,
            failed = result.failed,
            durationMs = System.currentTimeMillis() - startMs
        )
    }

    @Transactional
    fun backfillFileAttachments(batchSize: Int, dryRun: Boolean = false): BackfillResult {
        val startMs = System.currentTimeMillis()
        val page = fileAttachmentRepository.findImagesWithoutVariants(PageRequest.of(0, batchSize))
        val total = page.totalElements
        var processed = 0
        var skipped = 0
        var failed = 0

        if (dryRun) {
            return BackfillResult(
                total = total,
                processed = 0,
                skipped = page.content.size,
                failed = 0,
                durationMs = System.currentTimeMillis() - startMs
            )
        }

        for (fileAttachment in page.content) {
            try {
                val uri = java.net.URI(fileAttachment.url)
                val pathParts = uri.path.removePrefix("/").split("/", limit = 2)
                if (pathParts.size < 2) {
                    // URL 파싱 실패 → 원본 URL로 채워서 재조회 방지 (viewer 포함 3종 모두)
                    pinToOriginalUrl(fileAttachment.id, fileAttachment.url, fileAttachment.width, fileAttachment.height)
                    skipped++; continue
                }
                val bucket = pathParts[0]
                val key = pathParts[1]

                val s3Object = amazonS3.getObject(bucket, key)
                val image = ImageIO.read(s3Object.objectContent)
                if (image == null) {
                    // 디코딩 실패 → 원본 URL로 채워서 재조회 방지
                    pinToOriginalUrl(fileAttachment.id, fileAttachment.url, fileAttachment.width, fileAttachment.height)
                    skipped++; continue
                }

                val variantUrls = fileService.generateVariants(
                    image = image,
                    baseFilename = key.substringBeforeLast("."),
                    mimeType = fileAttachment.mimeType,
                    bucket = bucket
                )

                fileAttachmentRepository.updateAllVariantUrls(
                    id = fileAttachment.id,
                    thumbUrl = variantUrls["thumb"] ?: fileAttachment.url,
                    medUrl = variantUrls["medium"] ?: fileAttachment.url,
                    viewerUrl = variantUrls["viewer"] ?: fileAttachment.url,
                    width = fileAttachment.width ?: image.width,
                    height = fileAttachment.height ?: image.height
                )
                if (variantUrls.isEmpty()) skipped++ else processed++
            } catch (e: Exception) {
                log.warn("Backfill failed for FileAttachment id={}: {}", fileAttachment.id, e.message)
                // 실패해도 원본 URL로 채워서 무한 반복 방지 (viewer 포함)
                try {
                    pinToOriginalUrl(fileAttachment.id, fileAttachment.url, fileAttachment.width, fileAttachment.height)
                } catch (_: Exception) { }
                failed++
            }
        }

        return BackfillResult(
            total = total,
            processed = processed,
            skipped = skipped,
            failed = failed,
            durationMs = System.currentTimeMillis() - startMs
        )
    }

    /** 변형을 만들 수 없는 행은 원본 URL 로 3종을 채워 재조회 루프를 끊는다. */
    private fun pinToOriginalUrl(id: Long, url: String, width: Int?, height: Int?) {
        fileAttachmentRepository.updateAllVariantUrls(
            id = id,
            thumbUrl = url,
            medUrl = url,
            viewerUrl = url,
            width = width ?: 0,
            height = height ?: 0
        )
    }

    /**
     * GIF 원본만 있고 변형이 없는 행에 첫 프레임 기반 변형을 만들어 채운다.
     *
     * 1회성이므로 admin endpoint 로만 노출하고 `@EventListener(ApplicationReadyEvent)` 는
     * 걸지 않는다 (매 기동마다 S3 다운로드 반복 위험).
     */
    @Transactional
    fun backfillGifImageVariants(): GifBackfillResult {
        val targets = fileAttachmentRepository.findGifImagesWithoutVariants()
        var updated = 0
        var failed = 0
        val publicPrefix = "$publicEndpoint/$publicBucketName/"
        for (fa in targets) {
            try {
                val url = fa.url
                if (!url.startsWith(publicPrefix)) {
                    log.warn("GIF backfill skip: url not in public bucket: faId={}, url={}", fa.id, url)
                    failed++
                    continue
                }
                val key = url.substring(publicPrefix.length)
                val bytes = amazonS3.getObject(publicBucketName, key).use { obj ->
                    obj.objectContent.use { it.readBytes() }
                }
                val firstFrame = ImageIO.read(ByteArrayInputStream(bytes))
                if (firstFrame == null) {
                    log.warn("GIF backfill skip: decode failed: faId={}, url={}", fa.id, url)
                    failed++
                    continue
                }
                val baseFilename = key.substringBeforeLast(".")
                // mimeType 을 image/jpeg 으로 넘겨 generateVariants 의 GIF skip 분기를 우회.
                val urls = fileService.generateVariants(
                    firstFrame, baseFilename, "image/jpeg", publicBucketName, metricCategory = "gif-backfill"
                )
                if (urls.isEmpty()) {
                    log.warn("GIF backfill: generateVariants returned empty: faId={}", fa.id)
                    failed++
                    continue
                }
                fileAttachmentRepository.updateAllVariantUrls(
                    id = fa.id,
                    thumbUrl = urls["thumb"],
                    medUrl = urls["medium"],
                    viewerUrl = urls["viewer"],
                    width = firstFrame.width,
                    height = firstFrame.height
                )
                updated++
                log.info("GIF variant backfilled: faId={}, url={}, thumb={}", fa.id, url, urls["thumb"])
            } catch (e: Exception) {
                log.warn("GIF variant backfill failed: faId={}, url={}: {}", fa.id, fa.url, e.message, e)
                failed++
            }
        }
        return GifBackfillResult(total = targets.size, updated = updated, failed = failed)
    }
}
