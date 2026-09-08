package com.goldpet.domain.chat.dto

import com.goldpet.domain.chat.entity.MessageType
import jakarta.validation.constraints.Size

data class ChatMessageRequest(
    val roomId: Long,
    val senderId: Long,
    val messageType: MessageType,
    @field:Size(max = 5000, message = "메시지는 5000자를 초과할 수 없습니다")
    val textContent: String? = null,
    val fileId: Long? = null,
    val emoticonId: Long? = null,
    val emojiCode: String? = null,
    val replyToId: Long? = null,
    /**
     * Optional client-generated echo key (T-chat-latency-v2 Step 1).
     * DB 비영속 — STOMP 요청 payload → 컨트롤러 로컬 → 응답 DTO 왕복으로만 사용.
     */
    @field:Size(max = 64, message = "clientMsgId 는 64자를 초과할 수 없습니다")
    val clientMsgId: String? = null
)
