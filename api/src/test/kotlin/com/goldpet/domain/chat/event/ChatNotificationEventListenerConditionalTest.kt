package com.goldpet.domain.chat.event

import com.goldpet.domain.chat.metrics.ChatNotificationMetrics
import com.goldpet.domain.metrics.ChatLatencyMetrics
import com.goldpet.domain.notification.service.NotificationService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * ChatNotificationEventListener @ConditionalOnProperty smoke 테스트.
 *
 * ApplicationContextRunner 로 Spring Boot 컨텍스트 조건 평가만 격리 검증 (DB/Redis 기동 없음).
 *
 * 검증 케이스:
 *  1. `goldpet.chat.async.enabled=false` → 빈 미등록 (기본 동기 경로 유지)
 *  2. 프로퍼티 미설정 (matchIfMissing=false) → 빈 미등록
 *  3. `goldpet.chat.async.enabled=true`  → 빈 정상 등록
 */
class ChatNotificationEventListenerConditionalTest {

    /**
     * 리스너 DI 에 필요한 최소 인프라 빈만 노출.
     * MeterRegistry + NotificationService mock 제공.
     */
    @Configuration
    open class MinimalInfraConfig {
        @Bean
        open fun meterRegistry() = SimpleMeterRegistry()

        @Bean
        open fun notificationService(): NotificationService = mock(NotificationService::class.java)
    }

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(
            MinimalInfraConfig::class.java,
            ChatNotificationEventListener::class.java,
            ChatNotificationMetrics::class.java,
            ChatLatencyMetrics::class.java,
        )

    // ── Case 1: async.enabled=false ────────────────────────────────────────

    @Test
    fun `goldpet_chat_async_enabled=false → ChatNotificationEventListener 빈 미등록`() {
        contextRunner
            .withPropertyValues("goldpet.chat.async.enabled=false")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(ChatNotificationEventListener::class.java)
            }
    }

    // ── Case 2: 프로퍼티 미설정 (matchIfMissing=false) ──────────────────────

    @Test
    fun `goldpet_chat_async_enabled 미설정 → ChatNotificationEventListener 빈 미등록`() {
        contextRunner
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(ChatNotificationEventListener::class.java)
            }
    }

    // ── Case 3: async.enabled=true ─────────────────────────────────────────

    @Test
    fun `goldpet_chat_async_enabled=true → ChatNotificationEventListener 빈 정상 등록`() {
        contextRunner
            .withPropertyValues("goldpet.chat.async.enabled=true")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ChatNotificationEventListener::class.java)
            }
    }
}
