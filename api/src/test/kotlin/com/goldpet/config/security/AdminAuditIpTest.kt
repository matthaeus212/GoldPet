// 감사 로그에 위조된 IP 가 사실처럼 기록되지 않는지 고정한다
package com.goldpet.config.security

import com.goldpet.common.net.TrustedClientIpResolver
import com.goldpet.domain.admin.service.AdminAuditService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.nullableArgumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * 감사 로그는 사고 **이후에** 꺼내 보는 기록이다. 여기에 공격자가 정한 IP 가 들어가면
 * 기록 자체가 오염된다(다른 사람을 가리키거나 출처를 감출 수 있다).
 *
 * XFF 맨 앞 값은 클라이언트가 주입 가능하고, `forward-headers-strategy: framework` 때문에
 * `remoteAddr` 도 그 값으로 덮인다. 따라서 nginx 가 덮어쓰는 X-Real-IP 만 신뢰한다.
 */
class AdminAuditIpTest {

    private lateinit var auditService: AdminAuditService
    private lateinit var interceptor: AdminAuditInterceptor

    private val realIp = "115.79.198.72"
    private val adminPath = "/api/v1/admin/users/42"

    @BeforeEach
    fun setUp() {
        auditService = mock()
        interceptor = AdminAuditInterceptor(auditService, TrustedClientIpResolver())
    }

    private fun request(realIp: String?, forwardedFor: String?, remoteAddr: String) =
        MockHttpServletRequest("GET", adminPath).apply {
            realIp?.let { addHeader("X-Real-IP", it) }
            forwardedFor?.let { addHeader("X-Forwarded-For", it) }
            this.remoteAddr = remoteAddr
            setAttribute("adminUserId", 1L)
        }

    /** 감사 로그에 실제로 기록된 IP. 나머지 인자는 nullable 이라 anyOrNull() 이어야 매칭된다. */
    private fun loggedIp(): String? {
        val captor = nullableArgumentCaptor<String>()
        verify(auditService).log(
            adminUserId = eq(1L),
            action = anyOrNull(),
            targetType = anyOrNull(),
            targetId = anyOrNull(),
            ipAddress = captor.capture(),
            requestPath = anyOrNull(),
            requestMethod = anyOrNull(),
            responseStatus = anyOrNull(),
            details = anyOrNull(),
            userAgent = anyOrNull(),
        )
        return captor.firstValue
    }

    @Test
    fun `위조된 X-Forwarded-For 가 아니라 X-Real-IP 를 기록한다`() {
        val spoofed = request(realIp = realIp, forwardedFor = "1.2.3.4", remoteAddr = "1.2.3.4")

        interceptor.afterCompletion(spoofed, MockHttpServletResponse(), Any(), null)

        assertEquals(realIp, loggedIp(), "위조 IP 가 감사 로그에 기록됐다")
    }

    /** 판정 불가면 그럴듯한 거짓값 대신 null 을 남긴다. '모름'이 오염된 사실보다 낫다. */
    @Test
    fun `실IP 판정이 불가하면 null 로 남긴다`() {
        val untrusted = request(realIp = null, forwardedFor = "1.2.3.4", remoteAddr = "1.2.3.4")

        interceptor.afterCompletion(untrusted, MockHttpServletResponse(), Any(), null)

        assertNull(loggedIp(), "신뢰할 수 없는 값이 기록됐다")
    }
}
