package com.goldpet.domain.walk.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.ObjectMetadata
import com.goldpet.domain.file.service.FileService
import com.goldpet.domain.walk.entity.WalkSpotType
import com.goldpet.domain.walk.repository.WalkSpotRepository
import net.coobird.thumbnailator.Thumbnails
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Per-row backfill for walk_spots variant keys. Lives in its own bean so
 * REQUIRES_NEW is honored (cross-bean call enters a Spring AOP proxy).
 *
 * Invariant: DB commit only after all S3 PUTs succeed — a pod crash mid-PUT
 * leaves the row eligible for the next sweep.
 */
@Service
class WalkSpotBackfillWorker(
    private val walkSpotRepository: WalkSpotRepository,
    private val amazonS3: AmazonS3,
    @Value("\${S3_PRIVATE_BUCKET_NAME:goldpet-private}") private val privateBucketName: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class BatchResult(
        val total: Long,
        val processed: Int,
        val skipped: Int,
        val failed: Int
    )

    data class ProcessResult(val outcome: Outcome) {
        enum class Outcome { PROCESSED, SKIPPED, FAILED, NOT_FOUND }
    }

    // `_medium`(600) 은 iOS WKWebView 메모리 완화용 스와이프 표시 변형. 이 워커가 예전에 thumb/viewer 만
    // 생성해 구 사진에 medium 이 없었고(파생 키 404 → 큰 사진 blank), 그래서 여기에 추가한다.
    // FileService.IMAGE_VARIANTS 와 동일 사이즈/품질을 유지한다.
    private val variants = listOf(
        Triple("_thumb", 200, 0.80),
        Triple("_medium", 600, 0.85),
        Triple("_viewer", 1600, 0.82)
    )

    /**
     * Processes a single walk_spot row. REQUIRES_NEW so one failure does not roll back
     * the caller's batch-level bookkeeping.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processRow(spotId: Long, dryRun: Boolean): ProcessResult {
        val spot = walkSpotRepository.findById(spotId).orElse(null)
            ?: return ProcessResult(ProcessResult.Outcome.NOT_FOUND)

        val rawKey = spot.imageUrl
        if (rawKey.isNullOrBlank() || spot.type != WalkSpotType.PHOTO) {
            return ProcessResult(ProcessResult.Outcome.SKIPPED)
        }
        // viewer 와 medium 이 모두 있어야 완료. 구 사진은 viewer 만 있고 medium 이 없으므로(예전 워커가
        // thumb/viewer 만 생성) 여기서 다시 잡혀 medium 을 채운다.
        if (spot.imageKeyViewer != null && spot.imageKeyMedium != null) {
            return ProcessResult(ProcessResult.Outcome.SKIPPED)
        }

        try {
            val s3Object = amazonS3.getObject(privateBucketName, rawKey)
            val image: BufferedImage? = s3Object.objectContent.use { ImageIO.read(it) }
            if (image == null) {
                if (!dryRun) {
                    // Retry-prevention: pin to original key so the sweep does not re-pick this row.
                    spot.imageKeyViewer = rawKey
                    spot.imageKeyThumb = rawKey
                    spot.imageKeyMedium = rawKey
                }
                return ProcessResult(ProcessResult.Outcome.SKIPPED)
            }

            val baseFilename = rawKey.substringBeforeLast(".")
            var viewerKey: String? = null
            var thumbKey: String? = null
            var mediumKey: String? = null
            // Always generate both variants. Thumbnailator never upscales, so when the
            // source is already below maxDim the result preserves source dimensions —
            // the savings come from JPEG re-encoding a lossless PNG (e.g. 1200x900 PNG
            // ~3MB → 1200x900 JPEG ~250KB, a ~12x reduction). Without this, any walk
            // photo whose longSide is below maxDim fell back to the raw original and
            // produced the "swipe hangs on 10/51" lag on iOS WKWebView.

            for ((suffix, maxDim, quality) in variants) {
                // 이미 키가 있는 변형(구 사진의 thumb/viewer)은 재생성하지 않는다 — medium 만 채우면 되므로
                // 불필요한 S3 GET/PUT 을 피한다. 기존 키를 그대로 재사용한다.
                val existingKey = when (suffix) {
                    "_thumb" -> spot.imageKeyThumb
                    "_medium" -> spot.imageKeyMedium
                    else -> spot.imageKeyViewer
                }
                if (existingKey != null) {
                    when (suffix) {
                        "_thumb" -> thumbKey = existingKey
                        "_medium" -> mediumKey = existingKey
                        else -> viewerKey = existingKey
                    }
                    continue
                }

                val variantFilename = "$baseFilename$suffix.jpg"
                val out = ByteArrayOutputStream()
                Thumbnails.of(image)
                    .size(maxDim, maxDim)
                    .outputFormat("JPEG")
                    .outputQuality(quality)
                    .toOutputStream(out)
                val bytes = out.toByteArray()
                if (!dryRun) {
                    val metadata = ObjectMetadata().apply {
                        contentType = "image/jpeg"
                        contentLength = bytes.size.toLong()
                        cacheControl = "public, max-age=31536000, immutable"
                    }
                    amazonS3.putObject(privateBucketName, variantFilename, ByteArrayInputStream(bytes), metadata)
                }
                when (suffix) {
                    "_thumb" -> thumbKey = variantFilename
                    "_medium" -> mediumKey = variantFilename
                    "_viewer" -> viewerKey = variantFilename
                }
            }

            if (dryRun) return ProcessResult(ProcessResult.Outcome.PROCESSED)

            // If short-side below every threshold, fall back to original key so we don't sweep forever.
            spot.imageKeyViewer = viewerKey ?: rawKey
            spot.imageKeyThumb = thumbKey ?: rawKey
            spot.imageKeyMedium = mediumKey ?: rawKey
            return ProcessResult(ProcessResult.Outcome.PROCESSED)
        } catch (e: AmazonS3Exception) {
            // 404 NoSuchKey / 403 forbidden: the original object is permanently unreachable.
            // Pin the keys to rawKey so the sweep does not re-pick this row forever.
            if (e.statusCode == 404 || e.statusCode == 403) {
                log.warn("Walk spot backfill skipping spotId={} (S3 {}): {}", spotId, e.statusCode, e.errorCode)
                if (!dryRun) {
                    spot.imageKeyViewer = rawKey
                    spot.imageKeyThumb = rawKey
                    spot.imageKeyMedium = rawKey
                }
                return ProcessResult(ProcessResult.Outcome.SKIPPED)
            }
            // Transient S3 error (5xx, throttling) — leave row eligible for the next sweep.
            log.warn("Walk spot backfill transient S3 error for spotId={} status={}: {}", spotId, e.statusCode, e.message, e)
            return ProcessResult(ProcessResult.Outcome.FAILED)
        } catch (e: Exception) {
            log.warn("Walk spot backfill failed for spotId={}: {}", spotId, e.message, e)
            return ProcessResult(ProcessResult.Outcome.FAILED)
        }
    }

    /**
     * Batch sweep driver. Queries eligible rows and calls [processRow] for each — the
     * cross-bean call path is what lets REQUIRES_NEW actually start a new transaction.
     */
    fun backfillBatch(batchSize: Int, dryRun: Boolean): BatchResult {
        val pageable = org.springframework.data.domain.PageRequest.of(0, batchSize)
        val page = walkSpotRepository.findSpotsWithoutVariantKeys(pageable)
        val total = page.totalElements
        var processed = 0
        var skipped = 0
        var failed = 0
        for (spot in page.content) {
            when (processRow(spot.id, dryRun).outcome) {
                ProcessResult.Outcome.PROCESSED -> processed++
                ProcessResult.Outcome.SKIPPED, ProcessResult.Outcome.NOT_FOUND -> skipped++
                ProcessResult.Outcome.FAILED -> failed++
            }
        }
        return BatchResult(total, processed, skipped, failed)
    }

}
