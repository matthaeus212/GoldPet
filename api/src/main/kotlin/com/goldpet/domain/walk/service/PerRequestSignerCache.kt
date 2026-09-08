package com.goldpet.domain.walk.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope
import java.time.Duration

/**
 * Per-request Caffeine cache for presigned URLs. @RequestScope bounds lifetime to the
 * HTTP request, so a single /walks/public/photos response dedupes shared variant keys
 * across spots; the bean dies with the response and the cache is GC'd.
 */
@Component
@RequestScope
class PerRequestSignerCache {
    private val cache: Cache<String, String> = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofSeconds(10))
        .build()

    fun cachedSign(key: String, loader: () -> String): String =
        cache.get(key) { loader() }!!
}
