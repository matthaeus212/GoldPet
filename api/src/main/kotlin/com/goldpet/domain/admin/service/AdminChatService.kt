package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.ChatMessageAdminResponse
import com.goldpet.domain.admin.controller.ChatRoomAdminResponse
import com.goldpet.domain.chat.repository.ChatMessageRepository
import com.goldpet.domain.chat.repository.ChatRoomRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
class AdminChatService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun getChatRooms(pageable: Pageable): Page<ChatRoomAdminResponse> {
        return chatRoomRepository.findAll(pageable).map { room ->
            val lastMessage = chatMessageRepository.findTopByChatRoomIdOrderByCreatedAtDesc(room.id)
            ChatRoomAdminResponse(
                id = room.id,
                name = room.title ?: "채팅방 #${room.id}",
                type = room.roomType.name,
                participantCount = room.participants.size,
                lastMessage = lastMessage?.textContent,
                lastMessageAt = lastMessage?.createdAt?.format(formatter),
                createdAt = room.createdAt.format(formatter) ?: ""
            )
        }
    }

    fun getRoomMessages(roomId: Long, pageable: Pageable): Page<ChatMessageAdminResponse> {
        return chatMessageRepository.findByChatRoomIdOrderByCreatedAtDesc(roomId, pageable).map { msg ->
            ChatMessageAdminResponse(
                id = msg.id,
                roomId = roomId,
                senderId = msg.sender?.id ?: 0,
                senderNickname = msg.sender?.nickname ?: "시스템",
                content = msg.textContent ?: "",
                type = msg.messageType.name,
                createdAt = msg.createdAt.format(formatter) ?: ""
            )
        }
    }

    @Transactional
    fun deleteMessage(messageId: Long) {
        chatMessageRepository.deleteById(messageId)
    }

    @Transactional
    fun deleteRoom(roomId: Long) {
        chatMessageRepository.deleteByChatRoomId(roomId)
        chatRoomRepository.deleteById(roomId)
    }
}
