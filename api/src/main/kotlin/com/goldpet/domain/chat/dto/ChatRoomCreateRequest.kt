package com.goldpet.domain.chat.dto

import com.goldpet.domain.chat.entity.ChatRoomType

data class ChatRoomCreateRequest(
    val roomType: ChatRoomType,
    val title: String? = null,
    val participantUserIds: List<Long> = emptyList(),
    val matchId: Long? = null
)
