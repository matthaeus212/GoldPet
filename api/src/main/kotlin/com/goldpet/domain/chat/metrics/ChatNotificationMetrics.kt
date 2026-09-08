package com.goldpet.domain.chat.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * T-chat-latency-v2 Step 2 — chat notification FCM 파이프라인용 카운터 3종.
 *
 * | 이름 | 의미 | 증가 시점 |
 * | --- | --- | --- |
 * | `chat.fcm.failure` | 1차 FCM 호출 예외 | 첫 송신 실패 직후 (재시도 전) |
 * | `chat.fcm.dropped` | 재시도 후에도 실패 → 영구 드롭 | retry 예외 catch 지점 |
 * | `chat.fcm.caller_runs` | 큐 포화로 CallerRunsPolicy 발동 | `AsyncConfig.chatNotificationExecutor` rejected handler |
 *
 * `@PostConstruct` 에서 3 카운터 모두 등록 — startup 부터 Prometheus scrape 시 라벨 누락 없이 0 으로 노출.
 */
@Component
class ChatNotificationMetrics(
    private val meterRegistry: MeterRegistry
) {
    lateinit var fcmFailure: Counter
    lateinit var fcmDropped: Counter
    lateinit var fcmCallerRuns: Counter

    @PostConstruct
    fun register() {
        fcmFailure = Counter.builder(METRIC_FCM_FAILURE)
            .description("Chat FCM first-attempt failures before retry")
            .register(meterRegistry)
        fcmDropped = Counter.builder(METRIC_FCM_DROPPED)
            .description("Chat FCM messages dropped after single retry failure")
            .register(meterRegistry)
        fcmCallerRuns = Counter.builder(METRIC_FCM_CALLER_RUNS)
            .description("chatNotificationExecutor CallerRunsPolicy fallback count")
            .register(meterRegistry)
    }

    companion object {
        const val METRIC_FCM_FAILURE = "chat.fcm.failure"
        const val METRIC_FCM_DROPPED = "chat.fcm.dropped"
        const val METRIC_FCM_CALLER_RUNS = "chat.fcm.caller_runs"
    }
}
