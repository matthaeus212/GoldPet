package com.goldpet.domain.walk.service

import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.file.service.FileService
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

/**
 * Signs walk-photo S3 keys into time-limited presigned HTTPS URLs.
 *
 * Flags:
 *  - WALK_PHOTO_PRESIGNED_URL_ENABLED — master switch for presigned URLs
 *  - WALK_PHOTO_VARIANTS_ENABLED — gates the variant (viewer/thumb) paths; requires presigned flag too
 *  - WALK_PHOTO_SIGNER_CACHE_ENABLED — opt-in per-request Caffeine cache
 *  - WALK_PHOTO_SHARED_SIGNER_CACHE_ENABLED — 키당 URL 을 창 동안 고정하는 공유 캐시(기본 ON, kill-switch)
 *
 * Rejects and returns null for: null/blank input, inputs already prefixed with `http`,
 * and any input that does not match the UUID/variant filename pattern.
 */
@Component
class PhotoUrlSigner(
    private val fileService: FileService,
    private val systemSettingService: SystemSettingService,
    private val meterRegistry: MeterRegistry,
    private val signerCacheProvider: ObjectProvider<PerRequestSignerCache>,
    private val signedUrlCache: SignedUrlCache
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun registerMetrics() {
        Gauge.builder(GAUGE_FLAG_STATE, systemSettingService) { svc ->
            if (svc.getString(FLAG_KEY, FLAG_DEFAULT).equals("true", ignoreCase = true)) 1.0 else 0.0
        }
            .description("1 if WALK_PHOTO_PRESIGNED_URL_ENABLED flag is on across this pod, 0 if off")
            .register(meterRegistry)
    }

    fun signedUrlOrNull(imageKey: String?): String? {
        if (imageKey.isNullOrBlank()) return null
        if (imageKey.startsWith("http", ignoreCase = true)) return null
        if (!UUID_KEY_REGEX.matches(imageKey)) {
            log.warn("signedUrlOrNull rejected non-UUID key: {}", imageKey)
            return null
        }
        if (!presignedEnabled()) return null
        return signWithCache(imageKey)
    }

    fun signedViewerUrlOrNull(viewerKey: String?): String? = signVariantOrNull(viewerKey)

    fun signedThumbUrlOrNull(thumbKey: String?): String? = signVariantOrNull(thumbKey)

    // iOS WKWebView 메모리 누적 완화용 600px medium 변형 서명. VARIANT_KEY_REGEX 가 `_medium` 도
    // 매치하므로 viewer/thumb 와 동일 경로(presigned + variants 플래그 게이트)로 서명한다.
    fun signedMediumUrlOrNull(mediumKey: String?): String? = signVariantOrNull(mediumKey)

    private fun signVariantOrNull(key: String?): String? {
        if (key.isNullOrBlank()) return null
        if (key.startsWith("http", ignoreCase = true)) return null
        if (!VARIANT_KEY_REGEX.matches(key)) return null
        if (!presignedEnabled()) return null
        if (!variantsEnabled()) return null
        return signWithCache(key)
    }

    private fun presignedEnabled(): Boolean =
        systemSettingService.getString(FLAG_KEY, FLAG_DEFAULT).equals("true", ignoreCase = true)

    private fun variantsEnabled(): Boolean =
        systemSettingService.getString(FLAG_VARIANTS_KEY, FLAG_VARIANTS_DEFAULT).equals("true", ignoreCase = true)

    private fun signerCacheEnabled(): Boolean =
        systemSettingService.getString(FLAG_SIGNER_CACHE_KEY, "false").equals("true", ignoreCase = true)

    private fun sharedSignerCacheEnabled(): Boolean =
        systemSettingService.getString(FLAG_SHARED_SIGNER_CACHE_KEY, FLAG_SHARED_SIGNER_CACHE_DEFAULT)
            .equals("true", ignoreCase = true)

    private fun signWithCache(key: String): String {
        val sign = { fileService.getPresignedUrl(key, PRESIGN_TTL_MINUTES) }
        // 공유 캐시(기본 ON): 같은 키는 창 동안 같은 URL → <img src> 가 응답마다 바뀌지 않아
        // 브라우저 이미지 캐시가 적중하고, 같은 사진이 메모리에 여러 벌 디코딩되지 않는다.
        // per-request 캐시는 이 캐시의 부분집합이므로 공유 캐시가 켜져 있으면 거치지 않는다.
        if (sharedSignerCacheEnabled()) return signedUrlCache.cachedSign(key, sign)
        if (!signerCacheEnabled()) return sign()
        val cache = signerCacheProvider.ifAvailable ?: return sign()
        return cache.cachedSign(key, sign)
    }

    companion object {
        const val FLAG_KEY = "WALK_PHOTO_PRESIGNED_URL_ENABLED"
        const val FLAG_DEFAULT = "false"
        const val FLAG_VARIANTS_KEY = "WALK_PHOTO_VARIANTS_ENABLED"
        const val FLAG_VARIANTS_DEFAULT = "false"
        const val FLAG_SIGNER_CACHE_KEY = "WALK_PHOTO_SIGNER_CACHE_ENABLED"
        const val FLAG_SHARED_SIGNER_CACHE_KEY = "WALK_PHOTO_SHARED_SIGNER_CACHE_ENABLED"
        const val FLAG_SHARED_SIGNER_CACHE_DEFAULT = "true"
        const val PRESIGN_TTL_MINUTES = 30L
        const val GAUGE_FLAG_STATE = "walk_photo_presigned_feature_flag_state"

        val UUID_KEY_REGEX = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(\\.[a-zA-Z0-9]+)?$",
            RegexOption.IGNORE_CASE
        )

        val VARIANT_KEY_REGEX = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_(viewer|thumb|medium)\\.(jpg|jpeg)$",
            RegexOption.IGNORE_CASE
        )
    }
}
