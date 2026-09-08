package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.service.AdminChatService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin Chat Management", description = "관리자 채팅 관리 API")
@RestController
@RequestMapping("/api/v1/admin/chat")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminChatController(
    private val adminChatService: AdminChatService
) {
    @Operation(summary = "채팅방 목록 조회")
    @GetMapping("/rooms")
    fun getChatRooms(pageable: Pageable): ResponseEntity<Page<ChatRoomAdminResponse>> {
        return ResponseEntity.ok(adminChatService.getChatRooms(pageable))
    }

    @Operation(summary = "채팅방 메시지 조회")
    @GetMapping("/rooms/{roomId}/messages")
    fun getRoomMessages(
        @PathVariable roomId: Long,
        pageable: Pageable
    ): ResponseEntity<Page<ChatMessageAdminResponse>> {
        return ResponseEntity.ok(adminChatService.getRoomMessages(roomId, pageable))
    }

    @Operation(summary = "메시지 삭제")
    @DeleteMapping("/messages/{messageId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteMessage(@PathVariable messageId: Long): ResponseEntity<Void> {
        adminChatService.deleteMessage(messageId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "채팅방 삭제")
    @DeleteMapping("/rooms/{roomId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteRoom(@PathVariable roomId: Long): ResponseEntity<Void> {
        adminChatService.deleteRoom(roomId)
        return ResponseEntity.noContent().build()
    }
}

data class ChatRoomAdminResponse(
    val id: Long,
    val name: String,
    val type: String,
    val participantCount: Int,
    val lastMessage: String?,
    val lastMessageAt: String?,
    val createdAt: String
)

data class ChatMessageAdminResponse(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val senderNickname: String,
    val content: String,
    val type: String,
    val createdAt: String
)
