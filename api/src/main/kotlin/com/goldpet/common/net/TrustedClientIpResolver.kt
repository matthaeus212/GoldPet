// nginx 가 덮어쓴 X-Real-IP 만 신뢰해 클라이언트 실IP 를 판정하는 리졸버 (보안 판정 전용)
package com.goldpet.common.net

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import java.net.InetAddress

/**
 * **보안 판정에는 `request.remoteAddr` 도 `X-Forwarded-For` 도 쓰면 안 된다.**
 *
 * nginx 설정(`mannam.conf`, api 서버블록):
 *  - `proxy_set_header X-Real-IP $remote_addr;`            → **덮어쓰기**. 클라이언트가 보낸 값은 폐기된다 → 신뢰 가능
 *  - `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;` → **append**. 맨 앞 값은 클라이언트가 주입 가능
 *
 * 그런데 `application.yml` 의 `server.forward-headers-strategy: framework` 때문에 Spring 의
 * ForwardedHeaderFilter 가 **XFF 맨 앞 값으로 `remoteAddr` 을 덮는다.** 즉 `remoteAddr` 도 스푸핑된다.
 * `X-Forwarded-For: 1.2.3.4` 한 줄이면 IP 기반 통제가 통째로 무력화된다.
 *
 * 안전성의 근거 체인(셋 다 성립해야 한다 — 실측 확인함):
 *  1. nginx 가 8081 로 프록시하는 모든 location 에서 X-Real-IP 를 덮어쓴다
 *  2. API 는 127.0.0.1 에만 바인딩된다 → nginx 를 건너뛴 직접 접속 불가
 *  3. 도메인 앞에 CDN/엣지 프록시가 없다 → $remote_addr 이 곧 클라이언트 공인 IP
 *
 * 판정 불가(헤더 부재·콤마 포함·파싱 실패)는 **거부**한다(fail-closed). 헤더가 없다는 건
 * nginx 를 거치지 않았다는 뜻이고, 그 경로는 신뢰할 수 없다.
 */
@Component
class TrustedClientIpResolver {

    /**
     * 신뢰 가능한 클라이언트 IP. 판정 불가면 null.
     *
     * null 을 어떻게 다룰지는 **호출부의 성격**에 달렸다:
     *  - 인증 통제(dev-login): 거부(fail-closed)
     *  - 레이트리밋: 공용 버킷(가용성 우선, 우회는 여전히 불가)
     *  - 감사·포렌식 기록: null 그대로 저장. 위조된 IP 를 사실처럼 남기느니 "모름"이 낫다.
     */
    fun resolveOrNull(request: HttpServletRequest): String? = Companion.resolveOrNull(request)

    companion object {
        const val HEADER = "X-Real-IP"

        /** DI 를 쓸 수 없는 곳(object, static 컨텍스트)을 위한 동일 로직. 상태가 없다. */
        fun resolveOrNull(request: HttpServletRequest): String? {
            val raw = request.getHeader(HEADER)?.trim()
            if (raw.isNullOrEmpty()) return null
            // nginx 는 항상 단일값으로 덮어쓴다. 콤마는 프록시 체인 이상 신호이므로 신뢰하지 않는다.
            if (raw.contains(',')) return null

            return try {
                // IPv4-mapped IPv6(::ffff:1.2.3.4) 는 IPv4 로 정규화한다.
                InetAddress.getByName(raw).hostAddress
            } catch (_: Exception) {
                null
            }
        }
    }
}
