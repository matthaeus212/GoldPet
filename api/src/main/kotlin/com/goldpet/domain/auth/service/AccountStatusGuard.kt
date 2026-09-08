// 로그인 시 계정 상태(휴면/탈퇴/정지)를 검사하고, 휴면이면 해제에 필요한 정보를 실어 보내는 가드
package com.goldpet.domain.auth.service

import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.auth.exception.AccountStatusException
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserStatus
import org.springframework.stereotype.Component

/**
 * STYLE-001: 이 검사는 AuthService 와 SocialLoginService 에 **각자 복사돼** 있었다.
 * 휴면 응답에 해제 정보를 실으려면 두 곳을 똑같이 고쳐야 하므로 한 곳으로 합친다.
 *
 * 휴면 사용자는 로그인이 차단되어 access token 을 받을 수 없다. 다만 여기까지 왔다는 것은
 * 자격증명(비밀번호/SNS)이 이미 검증됐다는 뜻이므로, 그 시점에만 단기 해제 토큰을 발급해
 * 본인 확인을 넘긴다. 화면에 띄울 날짜(마지막 접속·휴면 전환)도 함께 내려준다 —
 * 예전에는 이 값이 없어 화면이 'OOO' 와 가짜 날짜를 하드코딩하고 있었다.
 */
@Component
class AccountStatusGuard(
    private val jwtTokenProvider: JwtTokenProvider
) {
    fun check(user: User) {
        when (user.status) {
            UserStatus.ACTIVE -> return

            UserStatus.DORMANT -> throw AccountStatusException(
                userStatus = user.status,
                errorCode = "DORMANT_ACCOUNT",
                details = buildMap {
                    put("activationToken", jwtTokenProvider.generateDormantActivationToken(user.id))
                    user.lastLoginAt?.let { put("lastLoginAt", it.toString()) }
                    user.dormantAt?.let { put("dormantAt", it.toString()) }
                }
            )

            UserStatus.WITHDRAWN -> throw AccountStatusException(user.status, "WITHDRAWN_ACCOUNT")
            UserStatus.SUSPENDED -> throw AccountStatusException(user.status, "SUSPENDED_ACCOUNT")
        }
    }
}
