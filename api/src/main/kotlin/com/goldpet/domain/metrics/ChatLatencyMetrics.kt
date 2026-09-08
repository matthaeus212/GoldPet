package com.goldpet.domain.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Chat latency instrumentation helper (T-chat-latency-v2 Step 0).
 *
 * Exposes a single Timer `chat.send.server_latency_ms` with a `phase` tag so Step 2 can
 * split pre_async (current synchronous path) vs post_async (new AFTER_COMMIT event path)
 * by tag rather than by metric name — this preserves Grafana dashboards across the cutover.
 *
 * The two phase Timers are pre-registered at startup so `/actuator/prometheus` exposes both
 * labels even before any request (avoids "metric missing" alerts on Step 2 deploy).
 */
@Component
class ChatLatencyMetrics(
    private val meterRegistry: MeterRegistry
) {
    enum class Phase(val tag: String) {
        PRE_ASYNC("pre_async"),
        POST_ASYNC("post_async")
    }

    @PostConstruct
    fun preregister() {
        // Force registration so both label variants appear in /actuator/prometheus from startup.
        Phase.values().forEach { phase -> timerFor(phase) }
    }

    private fun timerFor(phase: Phase): Timer =
        Timer.builder(TIMER_NAME)
            .tag("phase", phase.tag)
            .publishPercentileHistogram(true)
            .register(meterRegistry)

    /** Time a send-message block and record under `phase=<phase>`. */
    fun <T> recordServerLatency(phase: Phase, block: () -> T): T {
        val sample = Timer.start(meterRegistry)
        try {
            return block()
        } finally {
            sample.stop(timerFor(phase))
        }
    }

    companion object {
        const val TIMER_NAME = "chat.send.server_latency_ms"
    }
}
