package com.goldpet.domain.auth.controller

import com.goldpet.config.openapi.OpenApiInternal
import com.goldpet.domain.auth.dto.AuthResponse
import com.goldpet.domain.auth.service.AuthService
import com.goldpet.domain.auth.service.DevLoginAccessPolicy
import com.goldpet.domain.common.exception.ForbiddenException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * dev-login — 비밀번호도 SNS 인증도 없이 토큰을 발급하는 QA 전용 엔드포인트.
 *
 * 시드계정 QA 를 위해 라이브에 유지하지만 그 자체가 인증 우회 표면이므로, [DevLoginAccessPolicy] 가
 * **IP 허용목록 + 이메일 허용목록 + 킬스위치**로 이중 통제한다. 셋 다 fail-closed 이고 SystemSetting
 * 이라 재배포 없이 조작할 수 있다.
 *
 * 통제를 SecurityConfig 의 matcher 순서에 의존하지 않고 **유일한 호출지점인 여기**에 둔다 —
 * auth 하위 경로의 permitAll 과 선언 순서가 바뀌면 조용히 다시 열리는 설계는 위험하다.
 */
@OpenApiInternal
@RestController
@RequestMapping("/api/v1/auth")
@Profile("local", "dev")
class DevAuthController(
    private val authService: AuthService,
    private val devLoginAccessPolicy: DevLoginAccessPolicy
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이 요청 IP 에서 dev-login 이 가능한지만 알려준다.
     *
     * 로그인 화면의 개발자 패널은 이 값이 true 일 때만 렌더된다 — 허용 IP 가 아닌 사용자에게는
     * 입력칸 자체가 보이지 않는다(엔드포인트 존재를 광고하지 않음). 이메일 허용목록은 노출하지 않는다.
     */
    @GetMapping("/dev-login/available")
    fun devLoginAvailable(request: HttpServletRequest): ResponseEntity<Map<String, Boolean>> {
        val available = devLoginAccessPolicy.check(request) is DevLoginAccessPolicy.Decision.Allowed
        return ResponseEntity.ok(mapOf("available" to available))
    }

    @PostMapping("/dev-login")
    fun devLogin(
        @RequestBody(required = false) body: Map<String, String>?,
        request: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        when (val decision = devLoginAccessPolicy.check(request)) {
            is DevLoginAccessPolicy.Decision.Denied -> {
                log.warn("dev_login_denied reason={}", decision.reason)
                throw ForbiddenException("dev-login 이 허용되지 않았습니다.")
            }
            is DevLoginAccessPolicy.Decision.Allowed -> Unit
        }

        // 이메일 기본값("test@goldpet.com")으로 계정을 만들어주던 동작을 없앤다.
        // 허용목록에 없으면 어차피 거부되지만, 기본값 자체가 우회 시도의 발판이 될 이유가 없다.
        val email = body?.get("email")
            ?: throw ForbiddenException("dev-login 이 허용되지 않았습니다.")
        val nickname = body["nickname"] ?: "테스트유저"

        // 이메일 허용목록 검사는 AuthService.getOrCreateDevUser 최상단(모든 조회 이전)에서 수행된다.
        val user = authService.getOrCreateDevUser(email, nickname)
        log.info("dev_login_granted userId={}", user.id)
        return ResponseEntity.ok(authService.devLogin(user))
    }
}
