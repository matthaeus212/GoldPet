package com.goldpet.domain.metrics.controller

import com.goldpet.config.openapi.OpenApiInternal
import com.goldpet.config.security.UserPrincipal
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Dev-only client latency sink (T-chat-latency-v2 Step 0).
 *
 * Frontend posts RTT / image upload / first-paint durations here so we can compare Step 0
 * vs Step 2 side-by-side in Prometheus. Intentionally absent on prod — `@Profile("dev","local")`.
 *
 * Security: JWT required. `SecurityConfig` must NOT add `permitAll` for the metrics path;
 * the default `anyRequest().authenticated()` rule covers this endpoint.
 */
@OpenApiInternal
@RestController
@RequestMapping("/api/v1/metrics")
@Profile("local", "dev")
class MetricsController(
    private val meterRegistry: MeterRegistry
) {
    /** Per-user token bucket: 10 req/s. Resets lazily on each request. */
    private val buckets = ConcurrentHashMap<Long, AtomicReference<Bucket>>()

    @PostMapping("/chat")
    fun recordChatMetric(
        @RequestBody body: ChatMetricRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        if (body.metric !in ALLOWED_METRICS) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
        if (body.durationMs < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
        if (!allow(principal.id)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }

        Timer.builder(body.metric)
            .tag("source", "client")
            .publishPercentileHistogram(true)
            .register(meterRegistry)
            .record(Duration.ofMillis(body.durationMs))

        return ResponseEntity.noContent().build()
    }

    private fun allow(userId: Long): Boolean {
        val now = System.nanoTime()
        val ref = buckets.computeIfAbsent(userId) { AtomicReference(Bucket(now, RATE_PER_SEC.toDouble())) }
        while (true) {
            val current = ref.get()
            val elapsedSec = (now - current.lastRefillNanos).coerceAtLeast(0L) / 1_000_000_000.0
            val tokens = (current.tokens + elapsedSec * RATE_PER_SEC).coerceAtMost(RATE_PER_SEC.toDouble())
            if (tokens < 1.0) return false
            val next = Bucket(now, tokens - 1.0)
            if (ref.compareAndSet(current, next)) return true
        }
    }

    private data class Bucket(val lastRefillNanos: Long, val tokens: Double)

    data class ChatMetricRequest(
        @field:NotBlank val metric: String = "",
        val clientMsgId: String? = null,
        @field:NotNull @field:PositiveOrZero val durationMs: Long = 0L,
        val roomId: Long? = null
    )

    companion object {
        const val RATE_PER_SEC = 10
        val ALLOWED_METRICS = setOf(
            "chat.send.rtt_ms",
            "chat.image.upload_ms",
            "chat.list.open_to_first_paint_ms"
        )
    }
}
