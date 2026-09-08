// dev-login 접근 통제(IP/이메일/킬스위치) — 실제 공격 시나리오를 그대로 재현해 고정한다
package com.goldpet.domain.auth.service

import com.goldpet.common.net.TrustedClientIpResolver
import com.goldpet.domain.common.service.SystemSettingService
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest

class DevLoginAccessPolicyTest {

    private lateinit var systemSettingService: SystemSettingService
    private lateinit var ipAllowlistService: DevLoginIpAllowlistService
    private lateinit var policy: DevLoginAccessPolicy

    private val allowedIp = "115.79.198.72"
    private val outsiderIp = "203.0.113.9"

    @BeforeEach
    fun setUp() {
        systemSettingService = mock()
        ipAllowlistService = mock()
        policy = DevLoginAccessPolicy(systemSettingService, TrustedClientIpResolver(), ipAllowlistService)

        settings(
            enabled = "true",
            ips = "$allowedIp,211.207.145.74,127.0.0.1,::1",
            emails = "jioabang@kakao.com"
        )
    }

    private fun settings(enabled: String, ips: String, emails: String) {
        whenever(systemSettingService.getString(eq(DevLoginAccessPolicy.KEY_ENABLED), any())).thenReturn(enabled)
        whenever(systemSettingService.getString(eq(DevLoginAccessPolicy.KEY_ALLOWED_EMAILS), any())).thenReturn(emails)
        // 허용 IP 는 어드민이 관리하는 테이블에서 온다(캐시 없음).
        whenever(ipAllowlistService.activePatterns())
            .thenReturn(ips.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }

    /** nginx 를 거친 정상 요청을 흉내낸다(X-Real-IP 가 채워져 있다). */
    private fun request(realIp: String?, forwardedFor: String? = null): HttpServletRequest =
        MockHttpServletRequest().apply {
            realIp?.let { addHeader("X-Real-IP", it) }
            forwardedFor?.let { addHeader("X-Forwarded-For", it) }
            remoteAddr = "127.0.0.1"
        }

    private fun denyReason(d: DevLoginAccessPolicy.Decision): String =
        (d as DevLoginAccessPolicy.Decision.Denied).reason

    @Test
    fun `허용된 IP 는 통과한다`() {
        assertTrue(policy.check(request(allowedIp)) is DevLoginAccessPolicy.Decision.Allowed)
    }

    @Test
    fun `허용목록 밖 IP 는 거부된다`() {
        val d = policy.check(request(outsiderIp))
        assertTrue(denyReason(d).startsWith("ip_not_allowed"))
    }

    // 핵심 회귀: nginx 는 X-Forwarded-For 를 append 하므로 맨 앞 값은 클라이언트가 주입할 수 있다.
    // 그 값을 신뢰하면 헤더 한 줄로 IP 통제가 통째로 무력화된다. X-Real-IP 만 봐야 한다.
    @Test
    fun `X-Forwarded-For 를 위조해도 무력하다`() {
        val spoofed = request(realIp = outsiderIp, forwardedFor = "$allowedIp, 10.0.0.1")
        val d = policy.check(spoofed)
        assertTrue(denyReason(d).startsWith("ip_not_allowed"), "XFF 위조가 통과했다")
    }

    // nginx 를 거치지 않은 요청(= X-Real-IP 부재)은 신뢰할 수 없다. 열어두면 새 입구가 생기는 순간 뚫린다.
    @Test
    fun `X-Real-IP 가 없으면 거부한다 (fail-closed)`() {
        assertEquals("untrusted_ip", denyReason(policy.check(request(realIp = null))))
    }

    @Test
    fun `X-Real-IP 가 콤마를 포함하거나 파싱 불가면 거부한다`() {
        assertEquals("untrusted_ip", denyReason(policy.check(request("$allowedIp, 10.0.0.1"))))
        assertEquals("untrusted_ip", denyReason(policy.check(request("not-an-ip"))))
    }

    @Test
    fun `킬스위치가 꺼지면 IP 가 맞아도 전면 거부한다`() {
        settings(enabled = "false", ips = allowedIp, emails = "jioabang@kakao.com")
        assertEquals("disabled", denyReason(policy.check(request(allowedIp))))
    }

    // 설정 누락 = 열림 이면, 새 환경/새 DB 에서 조용히 뚫린다. 미설정은 닫힘이어야 한다.
    @Test
    fun `IP 허용목록이 비어 있으면 전면 거부한다 (미설정 = 닫힘)`() {
        settings(enabled = "true", ips = "", emails = "jioabang@kakao.com")
        assertEquals("no_ip_allowlist", denyReason(policy.check(request(allowedIp))))
    }

    @Test
    fun `CIDR 과 IPv6 루프백을 지원한다`() {
        settings(enabled = "true", ips = "10.1.2.0/24,::1", emails = "x@y.com")
        assertTrue(policy.check(request("10.1.2.77")) is DevLoginAccessPolicy.Decision.Allowed)
        assertTrue(policy.check(request("::1")) is DevLoginAccessPolicy.Decision.Allowed)
        assertTrue(policy.check(request("10.1.3.1")) is DevLoginAccessPolicy.Decision.Denied)
    }

    @Test
    fun `허용목록에 잘못된 패턴이 섞여 있어도 죽지 않고 나머지로 판정한다`() {
        settings(enabled = "true", ips = "쓰레기값,$allowedIp", emails = "x@y.com")
        assertTrue(policy.check(request(allowedIp)) is DevLoginAccessPolicy.Decision.Allowed)
    }

    // 계정탈취 회귀: 실사용자(카카오/네이버/구글) 이메일은 허용목록에 없으므로 거부돼야 한다.
    @Test
    fun `허용목록에 없는 이메일은 거부한다 (실사용자 계정탈취 차단)`() {
        assertFalse(policy.isEmailAllowed("victim@gmail.com"))
        assertTrue(policy.isEmailAllowed("jioabang@kakao.com"))
        assertTrue(policy.isEmailAllowed("  JIOABANG@KAKAO.COM  "), "대소문자/공백 정규화")
    }

    @Test
    fun `이메일 허용목록이 비어 있으면 어떤 이메일도 거부한다 (미설정 = 닫힘)`() {
        settings(enabled = "true", ips = allowedIp, emails = "")
        assertFalse(policy.isEmailAllowed("jioabang@kakao.com"))
    }
}
