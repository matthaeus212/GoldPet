package com.goldpet.common.ratelimit

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.common.net.TrustedClientIpResolver
import com.goldpet.config.security.UserPrincipal
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.ConsumptionProbe
import io.github.bucket4j.distributed.proxy.ProxyManager
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * community-author-profile-gallery §4-1 — per-viewer / per-IP rate-limit.
 *
 * 대상 경로(AntPathMatcher, `PROTECTED_PATTERNS` 하단 companion 참고):
 *   - `GET /api/v1/users/{userId}/public-profile`
 *   - `GET /api/v1/users/{userId}/community/posts`
 *
 * 로직:
 *   1. IP 버킷(기본 120 req/min) 을 먼저 consume — 미로그인 enumeration 방어.
 *   2. 로그인 상태(UserPrincipal) 면 viewer 버킷(기본 60 req/min) 도 consume.
 *   3. 어느 한 쪽이라도 고갈 → 429 + `Retry-After` 헤더 + JSON body.
 *
 * Redis 장애 시 `fail-open` — 로그만 찍고 다음 필터로 넘긴다. 가용성 우선.
 *
 * JWT 인증 필터 뒤에 배치되어야 `SecurityContextHolder` 가 채워진 상태로 viewerId 를 식별할 수 있다.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.rate-limit.user-profile",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class UserProfileRateLimitFilter(
    private val proxyManager: ProxyManager<ByteArray>,
    private val properties: UserProfileRateLimitProperties,
    private val objectMapper: ObjectMapper,
    private val trustedClientIpResolver: TrustedClientIpResolver,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pathMatcher = AntPathMatcher()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!properties.enabled) return true
        val path = request.requestURI ?: return true
        return PROTECTED_PATTERNS.none { pathMatcher.match(it, path) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val ip = resolveClientIp(request)
        val viewerId = resolveViewerId()

        try {
            // IP 먼저 — 익명/스푸핑 요청도 항상 제한.
            val ipProbe = tryConsume(ipBucketKey(ip), properties.ipPerMinute)
            if (!ipProbe.isConsumed) {
                reject(response, ipProbe, "ip", ip)
                return
            }

            if (viewerId != null) {
                val viewerProbe = tryConsume(viewerBucketKey(viewerId), properties.viewerPerMinute)
                if (!viewerProbe.isConsumed) {
                    reject(response, viewerProbe, "viewer", viewerId.toString())
                    return
                }
            }
        } catch (ex: Exception) {
            // Redis 장애 시 fail-open — SLA 우선. 이벤트는 기록.
            log.warn(
                "rate-limit filter bypassed due to upstream error: {} (path={}, ip={}, viewer={})",
                ex.javaClass.simpleName,
                request.requestURI,
                ip,
                viewerId,
            )
        }

        filterChain.doFilter(request, response)
    }

    private fun tryConsume(key: ByteArray, capacityPerMinute: Long): ConsumptionProbe {
        val bandwidth = Bandwidth.builder()
            .capacity(capacityPerMinute)
            .refillGreedy(capacityPerMinute, Duration.ofMinutes(1))
            .build()
        val config = BucketConfiguration.builder()
            .addLimit(bandwidth)
            .build()
        val bucket = proxyManager.builder().build(key) { config }
        return bucket.tryConsumeAndReturnRemaining(1)
    }

    private fun reject(
        response: HttpServletResponse,
        probe: ConsumptionProbe,
        dimension: String,
        subject: String,
    ) {
        val retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.nanosToWaitForRefill)
            .coerceAtLeast(1L)
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", retryAfterSeconds.toString())
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        val body = mapOf(
            "error" to "RATE_LIMIT",
            "dimension" to dimension,
            "retryAfter" to retryAfterSeconds,
        )
        response.writer.write(objectMapper.writeValueAsString(body))
        response.writer.flush()
        log.info(
            "rate-limit hit: dimension={} subject={} retryAfter={}s",
            dimension,
            subject,
            retryAfterSeconds,
        )
    }

    private fun resolveViewerId(): Long? {
        val principal = SecurityContextHolder.getContext().authentication?.principal ?: return null
        return (principal as? UserPrincipal)?.id
    }

    /**
     * X-Forwarded-For 맨 앞 값은 **클라이언트가 주입할 수 있다**(nginx 가 append 하므로).
     * 그 값을 버킷 키로 쓰면 매 요청 헤더만 바꿔 새 버킷을 얻는다 → IP 제한이 사실상 무제한이 된다.
     * `server.forward-headers-strategy: framework` 때문에 `remoteAddr` 도 XFF 로 덮여 같은 문제를 갖는다.
     * 따라서 nginx 가 덮어쓰는 X-Real-IP 만 신뢰한다([TrustedClientIpResolver]).
     *
     * 판정 불가는 **거부가 아니라 공용 버킷**이다. dev-login 은 fail-closed 지만 레이트리밋은 가용성이
     * 우선이고, 실사용 트래픽은 전부 nginx 를 거쳐 X-Real-IP 가 채워진다. 헤더를 지울 수 없는 이상
     * 공용 버킷으로 떨어뜨리는 것만으로 우회는 막힌다.
     */
    private fun resolveClientIp(request: HttpServletRequest): String =
        trustedClientIpResolver.resolveOrNull(request) ?: UNTRUSTED_IP_BUCKET

    private fun viewerBucketKey(viewerId: Long): ByteArray =
        "rl:userprof:viewer:$viewerId".toByteArray(StandardCharsets.UTF_8)

    private fun ipBucketKey(ip: String): ByteArray =
        "rl:userprof:ip:$ip".toByteArray(StandardCharsets.UTF_8)

    companion object {
        /** X-Real-IP 로 실IP 를 판정할 수 없는 요청이 함께 쓰는 버킷(개별 우회 불가). */
        const val UNTRUSTED_IP_BUCKET = "untrusted"

        private val PROTECTED_PATTERNS = listOf(
            "/api/v1/users/*/public-profile",
            "/api/v1/users/*/community/posts",
            "/api/v1/users/*/walks/photos",
        )
    }
}
