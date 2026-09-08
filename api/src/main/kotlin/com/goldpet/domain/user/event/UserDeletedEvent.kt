package com.goldpet.domain.user.event

import org.springframework.context.ApplicationEvent

class UserDeletedEvent(
    source: Any,
    val userId: Long,
    val anonymizedNickname: String = "탈퇴한 사용자"
) : ApplicationEvent(source)
