package com.goldpet.config

import com.goldpet.domain.chat.metrics.ChatNotificationMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.client.RestTemplate
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableAsync
class AsyncConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean(name = ["aiGenerationExecutor"])
    fun aiGenerationExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("ai-gen-")
        executor.initialize()
        return executor
    }

    /**
     * 산책 완료 보상-요약 푸시 전용 풀 (저빈도).
     *
     * 산책 종료는 사용자당 간헐 이벤트라 chat 만큼의 동시성이 필요 없음. 작은 풀로 충분하며,
     * `chatNotificationExecutor`(FCM hot path)와 분리해 상호 간섭을 막는다.
     * 포화 시 기본 `AbortPolicy` 대신 `CallerRunsPolicy` — 호출자(AFTER_COMMIT 리스너 스레드)에서
     * 실행되어 푸시 유실 대신 약간의 지연만 발생.
     */
    @Bean(name = ["walkNotificationExecutor"])
    fun walkNotificationExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("walk-notif-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.initialize()
        return executor
    }

    /**
     * PERF-006 — 좋아요/매칭 알림 발송 전용 풀 (저빈도).
     *
     * 좋아요/매칭은 사용자당 간헐 이벤트라 chat 만큼의 동시성이 필요 없음. walk 풀과 동일하게 작은 풀 +
     * 포화 시 `CallerRunsPolicy`(호출자=AFTER_COMMIT 리스너 스레드에서 실행 → 유실 대신 약간의 지연).
     * chat/walk 풀과 분리해 상호 간섭을 막는다.
     */
    @Bean(name = ["friendNotificationExecutor"])
    fun friendNotificationExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("friend-notif-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.initialize()
        return executor
    }

    /**
     * PERF-007 (적대적 리뷰 MEDIUM 후속) — 산책 생성 리버스 지오코딩 전용 풀.
     *
     * `WalkService.createWalk` 가 종료점 리버스 지오코딩(blocking HTTP, connect/read 3초)을
     * `CompletableFuture.supplyAsync`로 띄우는데, executor 를 지정하지 않으면 JVM 공용
     * `ForkJoinPool.commonPool()`을 쓰게 되어 동시 createWalk 요청이 몰릴 때 parallel
     * stream 등 애플리케이션 전역에서 공용 풀을 다투는 커먼풀 기아(starvation)를 유발할 수 있다.
     * `friendNotificationExecutor`/`walkNotificationExecutor` 와 같은 규율로 전용 bounded
     * executor 를 분리한다.
     *
     * ## 크기 근거
     * 블로킹 I/O(HTTP 3초 타임아웃)이고 호출자가 `.join()`으로 대기하는 요청 경로(비-이벤트)라
     * walk 알림 풀보다 여유를 둔다 — core=4/max=8, queue=50. 포화 시 `CallerRunsPolicy`:
     * 호출자(요청 스레드)에서 동기 실행 — 병렬화 이득만 사라질 뿐 기존(픽스 전) 순차 동작과
     * 동일해 유실이 없다.
     */
    @Bean(name = ["geocodingExecutor"])
    fun geocodingExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 4
        executor.maxPoolSize = 8
        executor.queueCapacity = 50
        executor.setThreadNamePrefix("geocoding-")
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.initialize()
        return executor
    }

    /**
     * T-chat-latency-v2 Step 2 — chat 알림 전용 풀.
     *
     * `aiGenerationExecutor` 와 분리해 이미지 생성 장기 태스크와 FCM 푸시 단기 태스크가 풀을 다투지 않도록 함.
     *
     * ## 크기 근거
     * - core=4, max=16, queue=500.
     * - 대화방 평균 참여자 × 피크 동시 송신 (dev 관측 기준) 을 여유 있게 수용.
     * - 포화 시 `CallerRunsPolicy` 로 역압 — 호출자(이벤트 리스너 스레드) 에서 실행되어 큐 폭주 차단 + `chat.fcm.caller_runs` 카운터 증가로 경보.
     *
     * ## Lazy metric injection
     * `ObjectProvider<ChatNotificationMetrics>` 로 지연 주입 — 빈 초기화 순환 의존 방지.
     * CallerRunsPolicy 발동 시 metrics 빈이 아직 준비 안 됐을 가능성은 실질적으로 0 (`@PostConstruct` 에서 register) 이지만 방어적으로 null-safe.
     */
    @Bean(name = ["chatNotificationExecutor"])
    fun chatNotificationExecutor(metricsProvider: ObjectProvider<ChatNotificationMetrics>): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = CHAT_NOTIF_CORE_POOL
        executor.maxPoolSize = CHAT_NOTIF_MAX_POOL
        executor.queueCapacity = CHAT_NOTIF_QUEUE_CAPACITY
        executor.setThreadNamePrefix("chat-notif-")
        executor.setRejectedExecutionHandler(callerRunsWithMetric(metricsProvider))
        executor.initialize()
        return executor
    }

    private fun callerRunsWithMetric(metricsProvider: ObjectProvider<ChatNotificationMetrics>): RejectedExecutionHandler {
        val delegate = ThreadPoolExecutor.CallerRunsPolicy()
        return RejectedExecutionHandler { runnable, executor ->
            runCatching { metricsProvider.ifAvailable?.fcmCallerRuns?.increment() }
                .onFailure { log.warn("Failed to increment chat.fcm.caller_runs counter: {}", it.message) }
            log.warn(
                "chatNotificationExecutor queue saturated (queue={}/{}, active={}/{}). Falling back to CallerRuns.",
                executor.queue.size, executor.queue.size + executor.queue.remainingCapacity(),
                executor.activeCount, executor.maximumPoolSize
            )
            delegate.rejectedExecution(runnable, executor)
        }
    }

    @Bean
    fun restTemplate(): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(30_000)
            setReadTimeout(180_000)  // 3분 - 이미지 생성은 시간이 오래 걸림
        }
        return RestTemplate(factory)
    }

    companion object {
        const val CHAT_NOTIF_CORE_POOL = 4
        const val CHAT_NOTIF_MAX_POOL = 16
        const val CHAT_NOTIF_QUEUE_CAPACITY = 500
    }
}
