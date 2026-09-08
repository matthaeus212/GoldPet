package com.goldpet.domain.chat.dto

import com.goldpet.domain.chat.entity.ChatRequest
import com.goldpet.domain.chat.entity.ChatRequestStatus
import com.goldpet.domain.common.util.toHttps
import java.time.LocalDateTime

data class ChatRequestResponse(
    val id: Long,
    val requesterNickname: String,
    val requesterProfileImage: String?,
    val status: ChatRequestStatus,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(chatRequest: ChatRequest): ChatRequestResponse {
            return ChatRequestResponse(
                id = chatRequest.id,
                requesterNickname = chatRequest.requester.nickname ?: "익명",
                requesterProfileImage = chatRequest.requester.profileImageUrl.toHttps(),
                status = chatRequest.status,
                createdAt = chatRequest.createdAt
            )
        }
    }
}
