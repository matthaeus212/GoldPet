package com.goldpet.domain.chat.controller

import com.goldpet.domain.chat.dto.ChatRoomCreateRequest
import com.goldpet.domain.chat.dto.ChatRoomResponse
import com.goldpet.domain.chat.dto.ChatMessageResponse
import com.goldpet.domain.chat.dto.ChatRequestResponse
import com.goldpet.domain.chat.dto.SendMessageRequest
import com.goldpet.domain.chat.service.ChatService
import com.goldpet.domain.metrics.ChatLatencyMetrics
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessageSendingOperations
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Chat", description = "채팅방 및 메시지 API")
@RestController
@RequestMapping("/api/v1/chat/rooms")
class ChatRoomController(
    private val chatService: ChatService,
    private val simpMessageSendingOperations: SimpMessageSendingOperations,
    private val chatLatencyMetrics: ChatLatencyMetrics
) {
    @Operation(summary = "채팅방 생성")
    @PostMapping
    fun createChatRoom(
        @RequestBody request: ChatRoomCreateRequest,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<ChatRoomResponse> {
        val chatRoom = chatService.createChatRoom(request, userDetails.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(ChatRoomResponse.from(chatRoom))
    }

    @Operation(summary = "채팅방 목록 조회")
    @GetMapping
    fun getChatRooms(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<List<ChatRoomResponse>> {
        val chatRooms = chatService.getUserChatRooms(userDetails.id)
        return ResponseEntity.ok(chatRooms.map { ChatRoomResponse.from(it, userDetails.id) })
    }

    @Operation(summary = "내 채팅방 목록 조회")
    @GetMapping("/my")
    fun getMyChatRooms(
        @AuthenticationPrincipal principal: org.springframework.security.core.userdetails.UserDetails
    ): ResponseEntity<List<ChatRoomResponse>> {
        val userId = if (principal is com.goldpet.config.security.UserPrincipal) {
            principal.id
        } else {
            // Fallback for other principal types if necessary
            // For now, assuming username lookup or throwing helpful error
             throw IllegalStateException("Unexpected principal type: ${principal::class.java}")
        }
        val chatRooms = chatService.getUserChatRooms(userId)
        return ResponseEntity.ok(chatRooms.map { ChatRoomResponse.from(it, userId) })
    }

    @Operation(summary = "채팅 메시지 목록 조회")
    @GetMapping("/{roomId}/messages")
    fun getChatMessages(
        @PathVariable roomId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<List<ChatMessageResponse>> {
        // EXT-CDX-003 (W1a): 참여자 검증은 ChatService 에 위임(seam-aware).
        // ARCH-003 (W4): 첨부/이모티콘 배치 조회와 응답 조립도 서비스로 이관 — 컨트롤러는 HTTP 만.
        return ResponseEntity.ok(chatService.getChatHistoryResponses(roomId, userDetails.id, page, size))
    }

    @Operation(summary = "메시지 전송")
    @PostMapping("/{roomId}/messages")
    fun sendMessage(
        @PathVariable roomId: Long,
        @Valid @RequestBody request: SendMessageRequest,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<ChatMessageResponse> = chatLatencyMetrics.recordServerLatency(ChatLatencyMetrics.Phase.PRE_ASYNC) {
        val messageRequest = com.goldpet.domain.chat.dto.ChatMessageRequest(
            roomId = roomId,
            senderId = userDetails.id,
            messageType = request.messageType,
            textContent = request.content,
            fileId = request.fileId,
            emoticonId = request.emoticonId,
            replyToId = request.replyToId,
            clientMsgId = request.clientMsgId
        )
        // ARCH-003: 저장 + 응답 조립은 서비스가 한 트랜잭션에서 끝낸다.
        // 브로드캐스트는 커밋 이후여야 하므로 여기(컨트롤러)에 남긴다.
        val sent = chatService.sendMessage(messageRequest)

        // Broadcast to WebSocket subscribers for real-time updates (chat detail page)
        simpMessageSendingOperations.convertAndSend("/topic/chat/$roomId", sent.response)

        // Broadcast to user-specific topics for chat list updates
        sent.participantUserIds.forEach { participantUserId ->
            simpMessageSendingOperations.convertAndSend(
                "/topic/user/$participantUserId/chats",
                mapOf("roomId" to roomId, "type" to "NEW_MESSAGE")
            )
        }

        ResponseEntity.status(HttpStatus.CREATED).body(sent.response)
    }

    @Operation(summary = "메시지 삭제")
    @DeleteMapping("/{roomId}/messages/{messageId}")
    fun deleteMessage(
        @PathVariable roomId: Long,
        @PathVariable messageId: Long,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<Void> {
        chatService.deleteMessage(roomId, messageId, userDetails.id)
        simpMessageSendingOperations.convertAndSend(
            "/topic/chat/$roomId",
            mapOf("type" to "MESSAGE_DELETED", "messageId" to messageId)
        )
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "메시지 읽음 처리")
    @PostMapping("/{roomId}/read")
    fun markAsRead(
        @PathVariable roomId: Long,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<Void> {
        // ARCH-003: 읽음 처리와 unreadCount 재계산은 서비스가 한다. 컨트롤러는 브로드캐스트만.
        val updates = chatService.markAsReadAndCollectUpdates(roomId, userDetails.id)
        if (updates.isEmpty()) {
            return ResponseEntity.ok().build()
        }

        // Broadcast enriched read event
        simpMessageSendingOperations.convertAndSend(
            "/topic/chat/$roomId/read",
            mapOf(
                "type" to "READ_UPDATE",
                "userId" to userDetails.id,
                "updates" to updates
            )
        )

        return ResponseEntity.ok().build()
    }

    @Operation(summary = "채팅방 초대")
    @PostMapping("/{roomId}/invite")
    fun inviteToRoom(
        @PathVariable roomId: Long,
        @RequestBody body: Map<String, Long>,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<Map<String, String>> {
        val userId = body["userId"] ?: throw IllegalArgumentException("userId is required")
        val chatRoom = chatService.inviteParticipant(roomId, userDetails.id, userId)

        // Broadcast system message if room was converted to GROUP
        val lastMessage = chatRoom.messages.lastOrNull()
        if (lastMessage != null && lastMessage.messageType == com.goldpet.domain.chat.entity.MessageType.SYSTEM) {
            simpMessageSendingOperations.convertAndSend(
                "/topic/chat/$roomId",
                ChatMessageResponse.from(lastMessage)
            )
        }

        return ResponseEntity.ok(mapOf("roomType" to chatRoom.roomType.name))
    }

    @Operation(summary = "채팅방 나가기")
    @DeleteMapping("/{roomId}/leave")
    fun leaveRoom(
        @PathVariable roomId: Long,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<Void> {
        chatService.leaveRoom(roomId, userDetails.id)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "1:1 채팅방 조회 또는 생성")
    @PostMapping("/direct/{targetUserId}")
    fun getOrCreateDirectRoom(
        @PathVariable targetUserId: Long,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<ChatRoomResponse> {
        val chatRoom = chatService.getOrCreateDirectRoom(userDetails.id, targetUserId)
        return ResponseEntity.ok(ChatRoomResponse.from(chatRoom, userDetails.id))
    }

    @Operation(summary = "채팅 요청 목록 조회")
    @GetMapping("/requests")
    fun getChatRequests(
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<List<ChatRequestResponse>> {
        val requests = chatService.getPendingRequests(userDetails.id)
        return ResponseEntity.ok(requests.map { ChatRequestResponse.from(it) })
    }

    @Operation(summary = "채팅 요청 수락")
    @PostMapping("/requests/{requestId}/accept")
    fun acceptChatRequest(
        @PathVariable requestId: Long,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<ChatRoomResponse> {
        val chatRoom = chatService.acceptRequest(requestId, userDetails.id)
        return ResponseEntity.ok(ChatRoomResponse.from(chatRoom, userDetails.id))
    }

    @Operation(summary = "채팅 요청 거절")
    @PostMapping("/requests/{requestId}/reject")
    fun rejectChatRequest(
        @PathVariable requestId: Long,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<Void> {
        chatService.rejectRequest(requestId, userDetails.id)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "채팅 요청 삭제")
    @DeleteMapping("/requests/{requestId}")
    fun deleteChatRequest(
        @PathVariable requestId: Long,
        @AuthenticationPrincipal userDetails: com.goldpet.config.security.UserPrincipal
    ): ResponseEntity<Void> {
        chatService.deleteRequest(requestId, userDetails.id)
        return ResponseEntity.ok().build()
    }
}
