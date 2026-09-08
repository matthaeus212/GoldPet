// 레이트리밋 버킷 키가 위조 가능한 헤더로 갈라지지 않는지 고정한다
package com.goldpet.common.ratelimit

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.common.net.TrustedClientIpResolver
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.ConsumptionProbe
import io.github.bucket4j.distributed.BucketProxy
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.charset.StandardCharsets
import java.util.function.Supplier

class UserProfileRateLimitFilterTest {

    private lateinit var proxyManager: ProxyManager<ByteArray>
    private lateinit var bucketBuilder: RemoteBucketBuilder<ByteArray>
    private lateinit var bucket: BucketProxy
    private lateinit var filter: UserProfileRateLimitFilter

    private val protectedPath = "/api/v1/users/7/public-profile"
    private val realIp = "203.0.113.9"

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()

        val probe: ConsumptionProbe = mock()
        whenever(probe.isConsumed).thenReturn(true)

        bucket = mock()
        whenever(bucket.tryConsumeAndReturnRemaining(any())).thenReturn(probe)

        bucketBuilder = mock()
        whenever(bucketBuilder.build(any<ByteArray>(), any<Supplier<BucketConfiguration>>())).thenReturn(bucket)

        proxyManager = mock()
        whenever(proxyManager.builder()).thenReturn(bucketBuilder)

        filter = UserProfileRateLimitFilter(
            proxyManager,
            UserProfileRateLimitProperties(),
            ObjectMapper(),
            TrustedClientIpResolver(),
        )
    }

    private fun call(
        realIp: String?,
        forwardedFor: String? = null,
        remoteAddr: String = "127.0.0.1",
    ) {
        val request = MockHttpServletRequest("GET", protectedPath).apply {
            realIp?.let { addHeader("X-Real-IP", it) }
            forwardedFor?.let { addHeader("X-Forwarded-For", it) }
            this.remoteAddr = remoteAddr
        }
        filter.doFilter(request, MockHttpServletResponse(), mock<FilterChain>())
    }

    /** 지금까지 만들어진 모든 버킷 키(mock 은 호출을 누적한다). */
    private fun bucketKeys(): List<String> {
        val captor = argumentCaptor<ByteArray>()
        verify(bucketBuilder, atLeastOnce()).build(captor.capture(), any<Supplier<BucketConfiguration>>())
        return captor.allValues.map { String(it, StandardCharsets.UTF_8) }
    }

    @Test
    fun `X-Real-IP 로 버킷을 만든다`() {
        call(realIp = realIp)
        assertEquals(listOf("rl:userprof:ip:$realIp"), bucketKeys())
    }

    /**
     * 핵심 회귀. nginx 는 XFF 를 append 하므로 맨 앞 값은 공격자가 정한다. 그 값을 키로 쓰면
     * 헤더만 바꿔가며 매 요청 새 버킷을 얻는다 = 레이트리밋 무력화(enumeration 방어가 사라진다).
     * `forward-headers-strategy: framework` 때문에 remoteAddr 도 같은 값으로 덮이므로 함께 재현한다.
     */
    @Test
    fun `X-Forwarded-For 를 위조해도 버킷이 갈라지지 않는다`() {
        // 같은 공격자가 헤더만 바꿔 두 번 호출한다.
        call(realIp = realIp, forwardedFor = "1.1.1.1", remoteAddr = "1.1.1.1")
        call(realIp = realIp, forwardedFor = "2.2.2.2", remoteAddr = "2.2.2.2")

        val keys = bucketKeys()
        assertEquals(2, keys.size)
        assertTrue(
            keys.all { it == "rl:userprof:ip:$realIp" },
            "위조 헤더로 버킷이 갈라졌다 — 레이트리밋 우회 가능: $keys",
        )
    }

    /**
     * 실IP 판정 불가(= nginx 를 거치지 않음)는 dev-login 처럼 거부하지 않고 공용 버킷으로 보낸다.
     * 레이트리밋은 가용성이 우선이고, 공용 버킷이면 개별 우회가 불가능하므로 목적은 달성된다.
     */
    @Test
    fun `X-Real-IP 가 없으면 거부가 아니라 공용 버킷을 쓴다`() {
        val chain: FilterChain = mock()
        val request = MockHttpServletRequest("GET", protectedPath).apply { remoteAddr = "9.9.9.9" }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)  // 통과시킨다(fail-open)
        assertEquals(200, response.status)

        val captor = argumentCaptor<ByteArray>()
        verify(bucketBuilder).build(captor.capture(), any<Supplier<BucketConfiguration>>())
        assertEquals(
            "rl:userprof:ip:${UserProfileRateLimitFilter.UNTRUSTED_IP_BUCKET}",
            String(captor.firstValue, StandardCharsets.UTF_8),
        )
    }

    @Test
    fun `보호 대상이 아닌 경로는 버킷을 만들지 않는다`() {
        val chain: FilterChain = mock()
        val request = MockHttpServletRequest("GET", "/api/v1/pets").apply { addHeader("X-Real-IP", realIp) }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        verify(proxyManager, never()).builder()
    }
}
