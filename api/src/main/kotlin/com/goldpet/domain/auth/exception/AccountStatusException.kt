package com.goldpet.domain.auth.exception

import com.goldpet.domain.user.entity.UserStatus

class AccountStatusException(
    val userStatus: UserStatus,
    val errorCode: String,
    /**
     * 클라이언트가 후속 동작을 하는 데 필요한 정보.
     * 휴면(DORMANT)이면 해제 토큰과 마지막 접속·휴면 전환 시각을 담는다(STYLE-001).
     */
    val details: Map<String, String>? = null
) : RuntimeException("Account is $userStatus")
