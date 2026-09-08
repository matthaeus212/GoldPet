package com.goldpet.domain.file.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.goldpet.domain.common.repository.FileAttachmentRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Per-row retry worker for T0-6. Targets rows the original backfill skip-marked
 * (thumbnailUrl = url / mediumUrl = url / viewerUrl NULL but thumbnailUrl set) and
 * attempts variant generation again. REQUIRES_NEW so one row's failure does not roll
 * back the batch driver's bookkeeping (mirrors WalkSpotBackfillWorker pattern).
 *
 * Cross-bean call through Spring AOP proxy is required for REQUIRES_NEW to take
 * effect — FileService calls `retryWorker.processRow(...)` not a private method.
 */
@Service
class FileAttachmentRetryWorker(
    private val amazonS3: AmazonS3,
    private val fileAttachmentRepository: FileAttachmentRepository,
    // ARCH-004: FileService 가 백필을 들고 있던 시절엔 서로를 참조해 @Lazy 로 순환을 우회해야 했다.
    // 백필이 FileBackfillService 로 빠지면서 의존이 한 방향이 됐으므로 @Lazy 가 필요 없다.
    private val fileService: FileService,
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    enum class Outcome { PROCESSED, SKIPPED, FAILED, NOT_FOUND }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processRow(attachmentId: Long, dryRun: Boolean): Outcome {
        val fa = fileAttachmentRepository.findById(attachmentId).orElse(null)
            ?: return Outcome.NOT_FOUND.also { recordResult("not_found") }

        if (fa.mimeType == "image/gif" || !fa.fileType.equals("IMAGE", ignoreCase = true)) {
            recordResult("skip")
            return Outcome.SKIPPED
        }

        val parsed = parseBucketAndKey(fa.url) ?: run {
            // URL parse failed (e.g. bare key private rows). Pin to url so we don't sweep forever.
            if (!dryRun) {
                fileAttachmentRepository.updateVariantUrls(
                    id = fa.id,
                    thumbUrl = fa.url,
                    medUrl = fa.url,
                    width = fa.width ?: 0,
                    height = fa.height ?: 0
                )
            }
            recordResult("skip")
            return Outcome.SKIPPED
        }
        val (bucket, key) = parsed

        return try {
            val image: BufferedImage? = amazonS3.getObject(bucket, key).use { s3Object ->
                s3Object.objectContent.use { ImageIO.read(it) }
            }
            if (image == null) {
                if (!dryRun) {
                    fileAttachmentRepository.updateVariantUrls(
                        id = fa.id,
                        thumbUrl = fa.url,
                        medUrl = fa.url,
                        width = fa.width ?: 0,
                        height = fa.height ?: 0
                    )
                }
                recordResult("skip")
                return Outcome.SKIPPED
            }

            if (dryRun) {
                recordResult("dryrun")
                return Outcome.PROCESSED
            }

            fileService.generateVariantsRaw(
                attachmentId = fa.id,
                image = image,
                baseFilename = key.substringBeforeLast("."),
                mimeType = fa.mimeType,
                bucket = bucket,
                isPublic = true,
                category = "retry"
            )
            recordResult("success")
            Outcome.PROCESSED
        } catch (e: AmazonS3Exception) {
            if (e.statusCode == 404 || e.statusCode == 403) {
                log.warn("FileAttachment retry skipping id={} (S3 {}): {}", attachmentId, e.statusCode, e.errorCode)
                if (!dryRun) {
                    fileAttachmentRepository.updateVariantUrls(
                        id = fa.id,
                        thumbUrl = fa.url,
                        medUrl = fa.url,
                        width = fa.width ?: 0,
                        height = fa.height ?: 0
                    )
                }
                recordResult("skip")
                Outcome.SKIPPED
            } else {
                log.warn("FileAttachment retry transient S3 error id={} status={}: {}", attachmentId, e.statusCode, e.message)
                recordResult("error")
                Outcome.FAILED
            }
        } catch (e: Exception) {
            log.warn("FileAttachment retry failed id={}: {}", attachmentId, e.message, e)
            recordResult("error")
            Outcome.FAILED
        }
    }

    private fun recordResult(result: String) {
        meterRegistry.counter("file_backfill_retry_total", "result", result).increment()
    }

    private fun parseBucketAndKey(url: String): Pair<String, String>? {
        return try {
            val uri = java.net.URI(url)
            val parts = uri.path.removePrefix("/").split("/", limit = 2)
            if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) null
            else parts[0] to parts[1]
        } catch (_: Exception) {
            null
        }
    }
}
