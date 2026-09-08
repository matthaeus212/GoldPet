// dev-login 접근 허용 여부를 판정하는 단일 정책 (킬스위치 + IP 허용목록 + 이메일 허용목록)
package com.goldpet.domain.auth.service

import com.goldpet.common.net.TrustedClientIpResolver
import com.goldpet.domain.common.service.SystemSettingService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Component

/**
 * `POST /api/v1/auth/dev-login` 은 **비밀번호도 SNS 인증도 없이 토큰을 발급**한다. 시드계정 QA 용으로
 * 라이브에 유지하기로 한 이상, 그 자체가 인증 우회 표면이므로 두 겹의 통제를 건다.
 *
 * ## 이 정책이 닫는 구멍 (전부 실측 확인된 라이브 취약점이었다)
 *
 * 1. **실사용자 계정탈취** — `AuthService.getOrCreateDevUser` 가 `findByEmailHash` 로 사용자를 찾을 때
 *    `oauthProvider` 를 가리지 않았다. 카카오/네이버/구글로 가입한 실사용자의 **이메일만 알면 그 계정의
 *    액세스 토큰이 발급**됐다. → **이메일 허용목록**으로 차단.
 * 2. **인증 없는 계정/골드 faucet** — 미등재 이메일이면 dev 계정을 새로 만들고 토큰을 줬다. 그 토큰이
 *    `sns-signup` 을 통과하므로 **무한 계정 생성 + 계정당 웰컴골드 30G** 가 가능했다.
 *    → 미등재 이메일은 **계정을 만들지 않고 거부**.
 * 3. **시드계정 자체의 탈취** — 이메일 허용목록만 있으면 "아무나 시드계정으로" 로그인할 수 있다.
 *    시드계정에는 오너의 실제 채팅·골드·산책 데이터가 있다. → **IP 허용목록**으로 차단.
 *
 * ## 전부 fail-closed
 * 설정이 없거나, IP 판정이 불가하거나, 허용목록이 비어 있으면 **거부**한다.
 * "설정을 깜빡해서 열려 있었다"가 이 엔드포인트에서는 곧 사고다.
 *
 * 모든 값은 SystemSetting 이므로 **재배포 없이** 바꿀 수 있다(캐시는 setValue 가 evict).
 * 어드민 화면에서 CRUD 하는 전용 테이블은 후속으로 올린다 — 지금은 구멍을 닫는 게 먼저다.
 */
@Component
class DevLoginAccessPolicy(
    private val systemSettingService: SystemSettingService,
    private val trustedClientIpResolver: TrustedClientIpResolver,
    private val ipAllowlistService: DevLoginIpAllowlistService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    sealed class Decision {
        object Allowed : Decision()
        data class Denied(val reason: String) : Decision()
    }

    /** 요청 단위 판정 — 킬스위치 + IP. 이메일은 [isEmailAllowed] 로 서비스 계층이 따로 확인한다. */
    fun check(request: HttpServletRequest): Decision {
        if (!enabled()) return Decision.Denied("disabled")

        val ip = trustedClientIpResolver.resolveOrNull(request)
            ?: return Decision.Denied("untrusted_ip")   // 헤더 부재/이상 → 거부

        val allowed = allowedIpPatterns()
        if (allowed.isEmpty()) return Decision.Denied("no_ip_allowlist")  // 미설정 = 닫힘

        val matched = allowed.any { pattern ->
            try {
                IpAddressMatcher(pattern).matches(ip)
            } catch (e: IllegalArgumentException) {
                log.warn("devlogin: invalid IP pattern in allowlist, ignoring: {}", pattern)
                false
            }
        }
        return if (matched) Decision.Allowed else Decision.Denied("ip_not_allowed:$ip")
    }

    /**
     * 이 이메일로 **기존 계정에 로그인**해도 되는가.
     *
     * false 면 호출부는 계정을 만들지도, 토큰을 주지도 않고 거부해야 한다.
     * (예전에는 미등재 이메일이면 새 dev 계정을 만들어 토큰을 줬다 — 그게 골드 faucet 이었다.)
     */
    fun isEmailAllowed(email: String): Boolean {
        val allowed = allowedEmails()
        if (allowed.isEmpty()) return false   // 미설정 = 닫힘
        return allowed.any { it.equals(email.trim(), ignoreCase = true) }
    }

    fun enabled(): Boolean =
        systemSettingService.getString(KEY_ENABLED, "false").equals("true", ignoreCase = true)

    /**
     * 허용 IP/CIDR. 어드민이 관리하는 `dev_login_ip_allowlist` 테이블에서 매번 읽는다.
     *
     * 일부러 캐시하지 않는다 — 어드민에서 IP 를 고치면 **즉시** 반영돼야 한다.
     * (SystemSetting 은 Redis 24h TTL 이라, 잠겼을 때 값을 고쳐도 최대 24시간 안 먹는다.
     *  잠김 복구가 필요한 바로 그 순간에 안 듣는 통제는 없느니만 못하다.)
     */
    fun allowedIpPatterns(): List<String> = ipAllowlistService.activePatterns()

    fun allowedEmails(): List<String> = csv(KEY_ALLOWED_EMAILS)

    private fun csv(key: String): List<String> =
        systemSettingService.getString(key, "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        const val KEY_ENABLED = "devlogin.enabled"
        const val KEY_ALLOWED_EMAILS = "devlogin.allowed.emails"
    }
}
