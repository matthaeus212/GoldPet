package com.goldpet.domain.metrics.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.user.entity.User
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class MetricsControllerTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var controller: MetricsController
    private lateinit var principal: UserPrincipal

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        controller = MetricsController(meterRegistry)
        val user = User(
            id = 1L,
            email = "test@example.com",
            oauthProvider = "GOOGLE",
            oauthId = "google123",
            username = null,
            password = null,
            nickname = "TestUser",
            name = null,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        principal = UserPrincipal.create(user)
    }

    @Test
    fun `accepts allowlisted metric and records timer`() {
        val response = controller.recordChatMetric(
            MetricsController.ChatMetricRequest(metric = "chat.send.rtt_ms", clientMsgId = "abc", durationMs = 120L, roomId = 1L),
            principal
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        val timer = meterRegistry.find("chat.send.rtt_ms").timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isEqualTo(1L)
    }

    @Test
    fun `rejects non-allowlisted metric with 400`() {
        val response = controller.recordChatMetric(
            MetricsController.ChatMetricRequest(metric = "attack.inject", durationMs = 0L),
            principal
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `rejects negative duration with 400`() {
        val response = controller.recordChatMetric(
            MetricsController.ChatMetricRequest(metric = "chat.send.rtt_ms", durationMs = -5L),
            principal
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `per-user rate-limit triggers 429 after 10 requests in same burst`() {
        // 10 rapid requests should pass
        repeat(10) {
            val r = controller.recordChatMetric(
                MetricsController.ChatMetricRequest(metric = "chat.send.rtt_ms", durationMs = 10L),
                principal
            )
            assertThat(r.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        }
        // 11th in the same tick should be rejected
        val rejected = controller.recordChatMetric(
            MetricsController.ChatMetricRequest(metric = "chat.send.rtt_ms", durationMs = 10L),
            principal
        )
        assertThat(rejected.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
    }
}
