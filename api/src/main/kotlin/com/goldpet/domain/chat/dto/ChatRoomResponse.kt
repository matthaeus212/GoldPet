package com.goldpet.domain.chat.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldpet.domain.chat.entity.ChatRoom
import com.goldpet.domain.chat.entity.ChatRoomType
import com.goldpet.domain.common.util.toHttps
import java.time.LocalDateTime

data class ParticipantInfo(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?
)

data class ChatRoomResponse(
    val id: Long,
    val roomType: ChatRoomType,
    val title: String?,
    val name: String,
    val ownerUserId: Long?,
    val matchId: Long?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val participants: List<ParticipantInfo>,
    val lastMessage: String?,
    val lastMessageTime: LocalDateTime?,
    val unreadCount: Int,
    @get:JsonProperty("isGroup")
    val isGroup: Boolean
) {
    companion object {
        fun from(chatRoom: ChatRoom, currentUserId: Long? = null): ChatRoomResponse {
            val activeParticipants = chatRoom.participants
                .filter { it.leftAt == null }
                .map { ParticipantInfo(it.user.id, it.user.nickname ?: "익명", it.user.profileImageUrl.toHttps()) }

            val allParticipants = chatRoom.participants
                .map { ParticipantInfo(it.user.id, it.user.nickname ?: "익명", it.user.profileImageUrl.toHttps()) }

            // For direct chats, use the other participant's name (fallback to left/withdrawn user's name)
            val displayName: String = when {
                chatRoom.title != null -> chatRoom.title!!
                chatRoom.roomType == ChatRoomType.DIRECT -> {
                    activeParticipants.firstOrNull { it.id != currentUserId }?.nickname
                        ?: allParticipants.firstOrNull { it.id != currentUserId }?.nickname
                        ?: "채팅"
                }
                else -> activeParticipants.joinToString(", ") { it.nickname }
            }

            val lastMessage = chatRoom.messages.maxByOrNull { it.createdAt }

            // Calculate unread count based on lastReadAt
            val unreadCount = if (currentUserId != null) {
                val participant = chatRoom.participants.find { it.user.id == currentUserId }
                val lastReadAt = participant?.lastReadAt
                if (lastReadAt != null) {
                    chatRoom.messages.count { it.createdAt > lastReadAt }
                } else {
                    chatRoom.messages.size
                }
            } else {
                0
            }

            return ChatRoomResponse(
                id = chatRoom.id,
                roomType = chatRoom.roomType,
                title = chatRoom.title,
                name = displayName,
                ownerUserId = chatRoom.ownerUser?.id,
                matchId = chatRoom.match?.id,
                createdAt = chatRoom.createdAt,
                updatedAt = chatRoom.updatedAt,
                participants = activeParticipants,
                lastMessage = lastMessage?.textContent,
                lastMessageTime = lastMessage?.createdAt,
                unreadCount = unreadCount,
                isGroup = chatRoom.roomType == ChatRoomType.GROUP
            )
        }
    }
}
