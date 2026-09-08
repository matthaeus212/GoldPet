package com.goldpet.domain.file.service

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.GpsDirectory
import com.goldpet.domain.common.exception.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import net.coobird.thumbnailator.Thumbnails
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Date
import java.util.UUID
import java.util.concurrent.RejectedExecutionException
import javax.imageio.ImageIO

private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024L // 10MB
private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml")

// V68 BLOCKER #6: EXIF GPS strip 대상 mime 화이트리스트.
// JPEG/PNG 만 ImageIO 표준 plugin 으로 re-encode 가능. WebP/HEIC 는 별도 plugin 의존성 sprint.
private val MIME_TYPES_WITH_METADATA_STRIP = setOf("image/jpeg", "image/png")

enum class StorageBucket {
    PUBLIC,  // Profile images, pet images - directly accessible via public URL
    PRIVATE  // Chat images, community posts - requires presigned URL
}

data class ImageVariant(
    val suffix: String,      // e.g., "_thumb", "_medium"
    val maxDimension: Int,   // max short-side pixels
    val quality: Double      // 0.0 - 1.0
)

data class ProcessedImage(
    val bytes: ByteArray,
    val bufferedImage: BufferedImage?,  // null for non-images or decode failures
    val width: Int?,
    val height: Int?
)

data class GifBackfillResult(val total: Int, val updated: Int, val failed: Int)

@Service
class FileService(
    private val amazonS3: AmazonS3,
    @Value("\${S3_PUBLIC_BUCKET_NAME:goldpet-public}") private val publicBucketName: String,
    @Value("\${S3_PRIVATE_BUCKET_NAME:goldpet-private}") private val privateBucketName: String,
    @Value("\${S3_PUBLIC_ENDPOINT:http://localhost:9100}") private val publicEndpoint: String,
    @Value("\${S3_ENDPOINT:http://localhost:9100}") private val s3Endpoint: String,
    private val fileAttachmentRepository: com.goldpet.domain.common.repository.FileAttachmentRepository,
    @org.springframework.beans.factory.annotation.Qualifier("variantExecutor")
    private val variantExecutor: ThreadPoolTaskExecutor,
    private val meterRegistry: MeterRegistry,
    private val systemSettingService: com.goldpet.domain.common.service.SystemSettingService,
    private val environment: Environment
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val IMAGE_VARIANTS = listOf(
        ImageVariant("_thumb", 200, 0.80),
        ImageVariant("_medium", 600, 0.85),
        ImageVariant("_viewer", 1600, 0.82)
    )

    // Pre-registered so /actuator/prometheus exposes the meter with value 0 before
    // T1-6 refresh endpoint wires the increment path.
    private val presignedExpiredFallbackCounter: Counter by lazy {
        Counter.builder("file_presigned_expired_fallback_total")
            .description("Presigned URL re-issued after client reports expiration")
            .register(meterRegistry)
    }

    init {
        // codegen 프로파일은 MinIO/S3 없이 openapi 스펙만 생성하므로 bucket 초기화 스킵.
        if (!environment.activeProfiles.contains("codegen")) {
            ensureBucket(publicBucketName, isPublic = true)
            ensureBucket(privateBucketName, isPublic = false)
        }
        // Force lazy counter init so the meter is exposed even before first increment.
        presignedExpiredFallbackCounter
    }

    /**
     * Builds S3 ObjectMetadata with the variant/static-image Cache-Control.
     * All image bytes uploaded via FileService are UUID-keyed and effectively immutable,
     * so "public, max-age=31536000, immutable" is safe for browsers and CDN.
     */
    private fun objectMetadataWithCache(contentType: String, length: Long): ObjectMetadata =
        ObjectMetadata().apply {
            this.contentType = contentType
            this.contentLength = length
            this.cacheControl = "public, max-age=31536000, immutable"
        }

    private fun recordVariantOutcome(
        result: String,
        category: String,
        sample: Timer.Sample
    ) {
        meterRegistry.counter(
            "file_variant_generate_total",
            "result", result,
            "category", category
        ).increment()
        sample.stop(
            Timer.builder("file_variant_generate_latency")
                .tag("category", category)
                .register(meterRegistry)
        )
    }

    private fun ensureBucket(bucket: String, isPublic: Boolean) {
        try {
            if (!amazonS3.doesBucketExistV2(bucket)) {
                amazonS3.createBucket(bucket)
            }
            if (isPublic) {
                val policy = """
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Sid": "PublicReadGetObject",
                            "Effect": "Allow",
                            "Principal": "*",
                            "Action": "s3:GetObject",
                            "Resource": "arn:aws:s3:::$bucket/*"
                        }
                    ]
                }
                """.trimIndent()
                amazonS3.setBucketPolicy(bucket, policy)
            }
        } catch (e: Exception) {
            log.warn("S3/MinIO bucket setup failed for '{}': {}", bucket, e.message)
        }
    }

    /**
     * Determines which bucket to use based on upload category.
     * PUBLIC: profile, pet, avatar images (directly accessible)
     * PRIVATE: chat, community, and all other uploads (presigned URL required)
     */
    private fun determineBucket(category: String?): StorageBucket {
        return when (category?.lowercase()) {
            "profile", "pet", "avatar", "community", "notice", "ai-profile", "chat", "badge", "emoticon", "banner" -> StorageBucket.PUBLIC
            else -> StorageBucket.PRIVATE
        }
    }

    /**
     * Allowlist for variant generation. Explicit scope to prevent silent scope leak:
     * PUBLIC bucket (all existing PUBLIC categories) + walk-photo uploads (category == null).
     */
    private fun needsVariants(category: String?, bucket: StorageBucket): Boolean =
        bucket == StorageBucket.PUBLIC || category == null

    private fun validateImageFile(file: MultipartFile) {
        if (file.size > MAX_IMAGE_SIZE) {
            throw BadRequestException("File size exceeds maximum allowed size of 10MB")
        }
        val contentType = file.contentType?.lowercase()
        if (contentType == null || contentType !in ALLOWED_IMAGE_TYPES) {
            throw BadRequestException("Unsupported file type: $contentType. Allowed types: ${ALLOWED_IMAGE_TYPES.joinToString()}")
        }
    }

    /**
     * Decode the image once: strip EXIF GPS (JPEG only), decode BufferedImage, read dimensions.
     * Returns ProcessedImage so callers can reuse the decoded BufferedImage without re-reading.
     */
    private fun processImageFile(originalBytes: ByteArray, mimeType: String): ProcessedImage {
        if (!mimeType.startsWith("image/")) {
            return ProcessedImage(originalBytes, null, null, null)
        }

        // V68 BLOCKER #6: Strip EXIF GPS for JPEG/PNG (location PII 보호).
        // PNG eXIf chunk 도 GPS 메타 보유 가능 → metadata-extractor 가 GpsDirectory 로 검출.
        // WebP/HEIC 는 별도 sprint (TwelveMonkeys WebP writer + libheif JNI 의존성).
        val strippedBytes = if (mimeType in MIME_TYPES_WITH_METADATA_STRIP) {
            stripImageMetadata(originalBytes, mimeType)
        } else {
            originalBytes
        }

        // Decode once — used for dimensions + variant generation
        val image = try {
            ImageIO.read(ByteArrayInputStream(strippedBytes))
        } catch (e: Exception) {
            log.warn("Failed to decode image: ${e.message}")
            null
        }

        return ProcessedImage(
            bytes = strippedBytes,
            bufferedImage = image,
            width = image?.width,
            height = image?.height
        )
    }

    /**
     * V68 BLOCKER #6: Strip EXIF (특히 GPS) metadata from image bytes.
     *
     * 지원 mime:
     *   - image/jpeg → ImageIO.write(..., "jpg") 로 re-encode (raw pixels 만 유지)
     *   - image/png  → ImageIO.write(..., "png") 로 re-encode (eXIf/tEXt chunks 손실)
     *
     * 보안 우선 — GPS 메타 보유 시에만 re-encode (no-op for clean images, latency minimal).
     * WebP/HEIC 는 ImageIO 표준 plugin 미지원 → 별도 sprint 처리.
     */
    private fun stripImageMetadata(fileBytes: ByteArray, mimeType: String): ByteArray {
        return try {
            val metadata = ImageMetadataReader.readMetadata(ByteArrayInputStream(fileBytes))
            val gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
            if (gpsDirectory == null || gpsDirectory.isEmpty) return fileBytes
            val image: BufferedImage = ImageIO.read(ByteArrayInputStream(fileBytes)) ?: return fileBytes
            val outputStream = ByteArrayOutputStream()
            val format = when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png"  -> "png"
                else -> return fileBytes
            }
            ImageIO.write(image, format, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            log.warn("Failed to strip image metadata ($mimeType): ${e.message}")
            fileBytes
        }
    }

    /**
     * Generate thumbnail and medium WebP variants from a pre-decoded BufferedImage.
     * Skips GIFs (animated). Skips variants where the image is already smaller than the threshold.
     */
    fun generateVariants(
        image: BufferedImage,
        baseFilename: String,
        mimeType: String,
        bucket: String,
        metricCategory: String = "backfill"
    ): Map<String, String> {
        val sample = Timer.start(meterRegistry)
        if (mimeType == "image/gif") {
            recordVariantOutcome("skip", metricCategory, sample)
            return emptyMap()
        }

        // Always generate: Thumbnailator never upscales, so a sub-threshold source
        // produces a re-encoded JPEG at source dimensions. This preserves the PNG→JPEG
        // compression benefit and fixes the portrait-aspect viewer bug (shortSide ≤
        // maxDim previously skipped viewer generation for any narrow image).
        return try {
            val urls = mutableMapOf<String, String>()
            @Suppress("UNUSED_VARIABLE")
            val longSide = maxOf(image.width, image.height)

            for (variant in IMAGE_VARIANTS) {
                val variantFilename = "${baseFilename}${variant.suffix}.jpg"
                val output = ByteArrayOutputStream()

                Thumbnails.of(image)
                    .size(variant.maxDimension, variant.maxDimension)
                    .outputFormat("JPEG")
                    .outputQuality(variant.quality)
                    .toOutputStream(output)

                val variantBytes = output.toByteArray()
                val variantMetadata = objectMetadataWithCache("image/jpeg", variantBytes.size.toLong())
                amazonS3.putObject(bucket, variantFilename, ByteArrayInputStream(variantBytes), variantMetadata)

                val url = "$publicEndpoint/$bucket/$variantFilename"
                urls[variant.suffix.removePrefix("_")] = url  // "thumb" -> url, "medium" -> url
            }
            recordVariantOutcome(if (urls.isEmpty()) "skip" else "success", metricCategory, sample)
            urls
        } catch (e: Exception) {
            recordVariantOutcome("error", metricCategory, sample)
            throw e
        }
    }

    /**
     * Generate variants and persist them directly to the FileAttachment row.
     * Runs OUTSIDE any outer transaction (submitted via variantExecutor post-commit).
     * For PRIVATE bucket, stores raw S3 keys in variant columns (callers presign on read).
     * For PUBLIC bucket, stores full HTTPS URLs.
     */
    fun generateVariantsRaw(
        attachmentId: Long,
        image: BufferedImage,
        baseFilename: String,
        mimeType: String,
        bucket: String,
        isPublic: Boolean,
        category: String? = null
    ) {
        val metricCategory = category?.lowercase() ?: if (isPublic) "public" else "walk-photo"
        val sample = Timer.start(meterRegistry)
        if (mimeType == "image/gif") {
            recordVariantOutcome("skip", metricCategory, sample)
            return
        }
        try {
            val results = mutableMapOf<String, String>()
            @Suppress("UNUSED_VARIABLE")
            val longSide = maxOf(image.width, image.height)

            for (variant in IMAGE_VARIANTS) {
                val variantFilename = "${baseFilename}${variant.suffix}.jpg"
                val output = ByteArrayOutputStream()

                Thumbnails.of(image)
                    .size(variant.maxDimension, variant.maxDimension)
                    .outputFormat("JPEG")
                    .outputQuality(variant.quality)
                    .toOutputStream(output)

                val variantBytes = output.toByteArray()
                val variantMetadata = objectMetadataWithCache("image/jpeg", variantBytes.size.toLong())
                amazonS3.putObject(bucket, variantFilename, ByteArrayInputStream(variantBytes), variantMetadata)

                val stored = if (isPublic) "$publicEndpoint/$bucket/$variantFilename" else variantFilename
                results[variant.suffix.removePrefix("_")] = stored

                // T2: WebP variant — non-fatal; JPEG fallback remains if encoder unavailable
                try {
                    val webpFilename = "${baseFilename}${variant.suffix}.webp"
                    val webpOut = ByteArrayOutputStream()
                    Thumbnails.of(image)
                        .size(variant.maxDimension, variant.maxDimension)
                        .outputFormat("webp")
                        .outputQuality(variant.quality)
                        .toOutputStream(webpOut)
                    val webpBytes = webpOut.toByteArray()
                    amazonS3.putObject(
                        bucket, webpFilename,
                        ByteArrayInputStream(webpBytes),
                        objectMetadataWithCache("image/webp", webpBytes.size.toLong())
                    )
                    results["${variant.suffix.removePrefix("_")}_webp"] =
                        if (isPublic) "$publicEndpoint/$bucket/$webpFilename" else webpFilename
                } catch (e: Exception) {
                    log.warn("webp variant-gen skipped attachment={} variant={}: {}", attachmentId, variant.suffix, e.message)
                }
            }

            if (results.isEmpty()) {
                recordVariantOutcome("skip", metricCategory, sample)
                return
            }
            fileAttachmentRepository.updateAllVariantUrlsWithWebp(
                id = attachmentId,
                thumbUrl = results["thumb"],
                medUrl = results["medium"],
                viewerUrl = results["viewer"],
                thumbWebpUrl = results["thumb_webp"],
                medWebpUrl = results["medium_webp"],
                viewerWebpUrl = results["viewer_webp"],
                width = image.width,
                height = image.height
            )
            recordVariantOutcome("success", metricCategory, sample)
        } catch (e: Exception) {
            recordVariantOutcome("error", metricCategory, sample)
            throw e
        }
    }

    /**
     * Store a file and route it to the correct bucket based on category.
     *
     * @param file      the uploaded file
     * @param userId    owner user ID
     * @param category  upload context: "profile", "pet", "avatar" -> public bucket;
     *                  anything else (null, "chat", "community", ...) -> private bucket
     * @return FileAttachment with url:
     *         - public bucket: direct public URL
     *         - private bucket: the file key (use getPresignedUrl() to obtain a time-limited URL)
     */
    @org.springframework.transaction.annotation.Transactional
    fun storeFile(
        file: MultipartFile,
        userId: Long,
        category: String? = null
    ): com.goldpet.domain.common.entity.FileAttachment {
        try {
            if (file.isEmpty) {
                throw BadRequestException("Failed to store empty file.")
            }
            val originalFilename = file.originalFilename ?: "unknown"
            val extension = originalFilename.substringAfterLast(".", "")
            val newFilename = "${UUID.randomUUID()}.$extension"

            val mimeType = file.contentType ?: "application/octet-stream"

            if (mimeType.startsWith("image/")) {
                validateImageFile(file)
            }

            // Decode image once: strip EXIF GPS + read BufferedImage + dimensions
            val processed = processImageFile(file.bytes, mimeType)

            val metadata = objectMetadataWithCache(mimeType, processed.bytes.size.toLong())

            val bucket = determineBucket(category)
            val targetBucket = if (bucket == StorageBucket.PUBLIC) publicBucketName else privateBucketName

            amazonS3.putObject(targetBucket, newFilename, ByteArrayInputStream(processed.bytes), metadata)

            // Public files get a direct URL; private files store only the key
            val fileUrl = if (bucket == StorageBucket.PUBLIC) {
                "$publicEndpoint/$targetBucket/$newFilename"
            } else {
                newFilename  // callers must use getPresignedUrl(fileKey) for access
            }

            val fileType = when {
                mimeType.startsWith("image/") -> "IMAGE"
                mimeType.startsWith("video/") -> "VIDEO"
                mimeType.startsWith("audio/") -> "AUDIO"
                else -> "FILE"
            }

            val fileAttachment = com.goldpet.domain.common.entity.FileAttachment(
                ownerUserId = userId,
                fileType = fileType,
                mimeType = mimeType,
                url = fileUrl,
                originalFileName = originalFilename,
                sizeBytes = file.size,
                width = processed.width,
                height = processed.height,
                thumbnailUrl = null,
                mediumUrl = null,
                viewerUrl = null
            )
            val saved = fileAttachmentRepository.save(fileAttachment)

            // Variants are generated POST-COMMIT so storeFile's @Transactional boundary
            // is not held during Thumbnailator + S3 PUT latency.
            // IMPORTANT: do NOT capture the BufferedImage in the closure — with queueCapacity=200
            // the heap could retain 200 × ~50MB = 10GB of decoded images.
            // Re-fetch bytes from S3 inside the async task (scheduler DLQ sweep uses the same pattern).
            if (processed.bufferedImage != null
                && mimeType != "image/gif"
                && needsVariants(category, bucket)
            ) {
                val attachmentId = saved.id
                val baseFilename = newFilename.substringBeforeLast(".")
                val isPublic = bucket == StorageBucket.PUBLIC
                val s3Key = newFilename
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCommit() {
                        try {
                            variantExecutor.submit {
                                try {
                                    val s3Object = amazonS3.getObject(targetBucket, s3Key)
                                    val reloaded: BufferedImage? = s3Object.objectContent.use { ImageIO.read(it) }
                                    if (reloaded == null) {
                                        val metricCategory = category?.lowercase()
                                            ?: if (isPublic) "public" else "walk-photo"
                                        meterRegistry.counter(
                                            "file_variant_generate_total",
                                            "result", "skip",
                                            "category", metricCategory
                                        ).increment()
                                        log.warn("async variant-gen could not decode S3 object for attachment={} key={}", attachmentId, s3Key)
                                        return@submit
                                    }
                                    generateVariantsRaw(
                                        attachmentId = attachmentId,
                                        image = reloaded,
                                        baseFilename = baseFilename,
                                        mimeType = mimeType,
                                        bucket = targetBucket,
                                        isPublic = isPublic,
                                        category = category
                                    )
                                } catch (e: Exception) {
                                    log.warn("async variant-gen failed for attachment={}: {}", attachmentId, e.message, e)
                                }
                            }
                        } catch (e: RejectedExecutionException) {
                            meterRegistry.counter(
                                "walk_photo_variant_rejected_total",
                                "reason", "queue_full"
                            ).increment()
                            log.warn(
                                "variant-gen task REJECTED for attachment={}; " +
                                    "will be reclaimed by WalkSpotVariantSyncScheduler DLQ sweep",
                                attachmentId, e
                            )
                        }
                    }
                })
            }

            return saved
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: IOException) {
            throw RuntimeException("Failed to store file.", e)
        } catch (e: Exception) {
            throw RuntimeException("Failed to upload to S3.", e)
        }
    }

    companion object {
        /** SEC-005 — presigned URL 최대 유효시간(분). viewer 재시청(6h) 경로 상한과 일치. */
        const val MAX_PRESIGN_MINUTES = 360L
    }

    /**
     * Generate a time-limited presigned URL for a file in the private bucket.
     *
     * @param fileKey          the key returned by storeFile() for a private-bucket upload
     * @param expirationMinutes how long the URL remains valid (default 60 minutes)
     * @return presigned HTTPS URL
     */
    /**
     * Get the raw file content from the private bucket as an S3Object.
     */
    fun getPrivateFileObject(fileKey: String): com.amazonaws.services.s3.model.S3Object {
        return amazonS3.getObject(privateBucketName, fileKey)
    }

    /**
     * T1-6 — category-aware presigned URL. "viewer" 경로는 public gallery 에서 재시청이
     * 잦고 수명 가시성이 필요하므로 6시간 opt-in. 그 외(chat attachment 등)는 60분 기본
     * 유지. 보안 민감도가 다른 경로 간 일관된 TTL 정책 분리.
     */
    fun getPresignedUrl(fileKey: String, category: String): String {
        val minutes = when (category.lowercase()) {
            "viewer" -> 360L // 6h public gallery viewer variant
            else -> 60L       // chat/file attachment default
        }
        return getPresignedUrl(fileKey, minutes)
    }

    fun getPresignedUrl(fileKey: String, expirationMinutes: Long = 60): String {
        // SEC-005: 만료 상한 서버 강제. 클라이언트가 임의로 긴 TTL 을 요청해 장기 유효 URL 을
        // 만들지 못하게 [1, MAX_PRESIGN_MINUTES] 로 clamp.
        val cappedMinutes = expirationMinutes.coerceIn(1L, MAX_PRESIGN_MINUTES)
        val expiration = Date(System.currentTimeMillis() + cappedMinutes * 60 * 1000)
        val request = GeneratePresignedUrlRequest(privateBucketName, fileKey)
            .withMethod(HttpMethod.GET)
            .withExpiration(expiration)
        val presignedUrl = amazonS3.generatePresignedUrl(request).toString()

        // Replace internal S3 endpoint with public endpoint so browsers/devices can access
        fun hostWithOptionalPort(urlStr: String): String {
            val uri = java.net.URI(urlStr)
            return if (uri.port > 0) "${uri.scheme}://${uri.host}:${uri.port}" else "${uri.scheme}://${uri.host}"
        }
        val internalHost = try { hostWithOptionalPort(s3Endpoint) } catch (_: Exception) { s3Endpoint }
        val publicHost = try { hostWithOptionalPort(publicEndpoint) } catch (_: Exception) { publicEndpoint }
        return presignedUrl.replace(internalHost, publicHost)
    }

}
