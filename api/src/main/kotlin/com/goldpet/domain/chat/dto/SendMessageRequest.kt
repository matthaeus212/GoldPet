package com.goldpet.domain.chat.dto

import com.goldpet.domain.chat.entity.MessageType
import jakarta.validation.constraints.Size

data class SendMessageRequest(
    @field:Size(max = 5000, message = "메시지는 5000자를 초과할 수 없습니다")
    val content: String? = null,
    val messageType: MessageType = MessageType.TEXT,
    val fileId: Long? = null,
    val emoticonId: Long? = null,
    val replyToId: Long? = null,
    /**
     * Optional client-generated idempotency/echo key (T-chat-latency-v2 Step 1).
     * DB 비영속 — 요청 → 컨트롤러 로컬 → 응답 DTO/STOMP 브로드캐스트 에코까지만 왕복한다.
     */
    @field:Size(max = 64, message = "clientMsgId 는 64자를 초과할 수 없습니다")
    val clientMsgId: String? = null
)
