package com.goldpet.domain.file.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ObjectMetadata
import com.goldpet.domain.common.repository.FileAttachmentRepository
import net.coobird.thumbnailator.Thumbnails
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.imageio.ImageIO

/**
 * T2 WebP backfill: generates WebP variants for rows that already have JPEG variants
 * (thumbnail_url IS NOT NULL) but are missing the WebP counterparts (thumbnail_url_webp IS NULL).
 *
 * Each JPEG variant is downloaded from S3, re-encoded to WebP at its existing dimensions,
 * and stored back to S3. Only the three WebP columns are updated in DB — JPEG columns
 * are never touched.
 *
 * Usage:
 *   POST /api/v1/admin/files/webp-backfill?dryRun=true&batchSize=100   — inspect only
 *   POST /api/v1/admin/files/webp-backfill?dryRun=false&batchSize=100  — live run
 */
@Service
class WebPBackfillService(
    private val amazonS3: AmazonS3,
    @Value("\${S3_PUBLIC_ENDPOINT:http://localhost:9100}") private val publicEndpoint: String,
    @Value("\${S3_PRIVATE_BUCKET_NAME:goldpet-private}") private val privateBucketName: String,
    private val fileAttachmentRepository: FileAttachmentRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class BackfillResult(
        val total: Long,
        val eligible: Int,
        val processed: Int,
        val succeeded: Int,
        val failed: Int,
        val mismatches: List<Long>
    )

    /**
     * Process up to [batchSize] rows that need WebP variants.
     *
     * @param dryRun   when true (default) no S3 writes or DB updates are performed — stats only
     * @param batchSize number of rows to process per invocation (clamped to 1–500)
     * @return [BackfillResult] with counts and IDs of failed rows
     */
    fun backfillBatch(dryRun: Boolean = true, batchSize: Int = 100): BackfillResult {
        val effectiveBatch = batchSize.coerceIn(1, 500)
        val pageable = PageRequest.of(0, effectiveBatch)
        val page = fileAttachmentRepository.findImagesWithJpegVariantsButNoWebp(pageable)
        val total = page.totalElements
        val eligible = page.content.size

        log.info("WebP backfill dryRun={} total={} eligible={}", dryRun, total, eligible)

        if (dryRun) {
            return BackfillResult(
                total = total,
                eligible = eligible,
                processed = 0,
                succeeded = 0,
                failed = 0,
                mismatches = emptyList()
            )
        }

        var succeeded = 0
        var failed = 0
        val mismatches = mutableListOf<Long>()

        for (fa in page.content) {
            try {
                // Convert thumbnail first — it is the required variant.
                // If it returns null (blank URL or unparseable), skip the row entirely:
                // no medium/viewer S3 calls, no DB write. This keeps thumbnail_url_webp
                // NULL by design so the row does NOT reappear in the next batch query.
                val thumbWebpUrl = fa.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { convertJpegToWebp(it) }
                if (thumbWebpUrl == null) {
                    log.warn(
                        "WebP backfill: thumb conversion null for id={} url='{}' — skipping to prevent infinite retry",
                        fa.id, fa.thumbnailUrl
                    )
                    mismatches.add(fa.id)
                    failed++
                } else {
                    val medWebpUrl = fa.mediumUrl?.takeIf { it.isNotBlank() }?.let { convertJpegToWebp(it) }
                    val viewerWebpUrl = fa.viewerUrl?.takeIf { it.isNotBlank() }?.let { convertJpegToWebp(it) }

                    fileAttachmentRepository.updateWebpVariantUrls(
                        id = fa.id,
                        thumbWebpUrl = thumbWebpUrl,
                        medWebpUrl = medWebpUrl,
                        viewerWebpUrl = viewerWebpUrl
                    )
                    succeeded++
                    log.debug("WebP backfill ok id={} thumb={}", fa.id, thumbWebpUrl)
                }
            } catch (e: Exception) {
                log.warn("WebP backfill failed id={}: {}", fa.id, e.message, e)
                mismatches.add(fa.id)
                failed++
            }
        }

        log.info(
            "WebP backfill complete: total={} eligible={} succeeded={} failed={}",
            total, eligible, succeeded, failed
        )
        return BackfillResult(
            total = total,
            eligible = eligible,
            processed = succeeded + failed,
            succeeded = succeeded,
            failed = failed,
            mismatches = mismatches
        )
    }

    /**
     * Downloads the JPEG at [variantUrl], re-encodes it as WebP at the same dimensions,
     * uploads to S3 (replacing the `.jpg` suffix with `.webp`), and returns the stored URL/key.
     *
     * Returns null if the URL cannot be parsed or the image cannot be decoded.
     */
    internal fun convertJpegToWebp(variantUrl: String): String? {
        // SVG는 벡터 포맷 — raster WebP로 변환 의미 없음.
        // Repository query에서 이미 제외하지만 service 단에서도 안전망.
        if (variantUrl.endsWith(".svg", ignoreCase = true)) {
            log.debug("WebP backfill: skipping SVG variant url='{}'", variantUrl)
            return null
        }
        val ref = parseVariantUrl(variantUrl) ?: run {
            log.warn("WebP backfill: cannot parse variant URL '{}'", variantUrl)
            return null
        }

        val imageBytes = amazonS3.getObject(ref.bucket, ref.key)
            .use { s3Obj -> s3Obj.objectContent.use { it.readBytes() } }
        val image = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: run {
            log.warn("WebP backfill: cannot decode image from bucket={} key={}", ref.bucket, ref.key)
            return null
        }

        val webpOut = ByteArrayOutputStream()
        Thumbnails.of(image)
            .scale(1.0)
            .outputFormat("webp")
            .outputQuality(0.85)
            .toOutputStream(webpOut)
        val webpBytes = webpOut.toByteArray()

        val webpKey = ref.key.substringBeforeLast(".") + ".webp"
        val metadata = ObjectMetadata().apply {
            contentType = "image/webp"
            contentLength = webpBytes.size.toLong()
            cacheControl = "public, max-age=31536000, immutable"
        }
        amazonS3.putObject(ref.bucket, webpKey, ByteArrayInputStream(webpBytes), metadata)

        return if (ref.isPublic) "$publicEndpoint/${ref.bucket}/$webpKey" else webpKey
    }

    private data class StorageRef(val bucket: String, val key: String, val isPublic: Boolean)

    /**
     * Parses a variant URL into a (bucket, key, isPublic) triple.
     *
     * - Full URL  (public):  "http://s3.test/goldpet-public/uuid_thumb.jpg"
     * - Bare key  (private): "uuid_thumb.jpg"
     */
    private fun parseVariantUrl(url: String): StorageRef? {
        return try {
            val uri = URI(url)
            if (uri.scheme != null) {
                // Full URL: path starts with /bucket/key
                val parts = uri.path.removePrefix("/").split("/", limit = 2)
                if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return null
                StorageRef(bucket = parts[0], key = parts[1], isPublic = true)
            } else {
                // Bare key → private bucket
                if (url.isBlank()) return null
                StorageRef(bucket = privateBucketName, key = url, isPublic = false)
            }
        } catch (_: Exception) {
            null
        }
    }
}
