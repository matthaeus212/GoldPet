package com.goldpet.domain.chat.dto

import com.goldpet.domain.chat.entity.ChatMessage
import com.goldpet.domain.chat.entity.ChatRoomParticipant
import com.goldpet.domain.chat.entity.MessageType
import com.goldpet.domain.common.entity.FileAttachment
import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.emoticon.entity.Emoticon
import java.time.LocalDateTime

data class FileInfo(
    val id: Long,
    val url: String,
    /** T1-2 Phase 5: FileAttachment.thumbnailUrl — 200px. 채팅 이미지 메시지 썸네일. */
    val urlThumbnail: String? = null,
    /** T1-2 Phase 5: FileAttachment.mediumUrl — 720px. 채팅 bubble 렌더용. */
    val urlMedium: String? = null,
    /** T1-2 Phase 5: FileAttachment.viewerUrl — 1600px. 탭하여 전체 화면 뷰어 진입 시. */
    val urlViewer: String? = null,
    val fileType: String,
    val mimeType: String,
    val originalFileName: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?
)

data class ChatMessageResponse(
    val id: Long,
    val roomId: Long,
    val senderId: Long?,
    val senderNickname: String?,
    val messageType: MessageType,
    val textContent: String?,
    val fileId: Long?,
    val file: FileInfo?,
    val emoticonId: Long?,
    val emoticonImageUrl: String?,
    /** T1-1.4: emoticon thumbnail variant (200px). T1-9 S3 이관된 이모티콘만 값 제공. */
    val emoticonImageUrlThumbnail: String? = null,
    /** T1-1.4: emoticon viewer variant (1600px). */
    val emoticonImageUrlViewer: String? = null,
    val emoticonName: String?,
    val emojiCode: String?,
    val createdAt: LocalDateTime,
    val unreadCount: Int,
    val replyToId: Long? = null,
    val replyToSenderNickname: String? = null,
    val replyToTextContent: String? = null,
    val replyToMessageType: MessageType? = null,
    val replyToFileUrl: String? = null,
    val replyToFileName: String? = null,
    val replyToEmoticonImageUrl: String? = null,
    /** T1-1.4: reply-to emoticon thumbnail variant. */
    val replyToEmoticonImageUrlThumbnail: String? = null,
    /** T1-1.4: reply-to emoticon viewer variant. */
    val replyToEmoticonImageUrlViewer: String? = null,
    val deletedAt: LocalDateTime? = null,
    /**
     * Client-generated echo key (T-chat-latency-v2 Step 1). DB 비영속.
     * 요청 body 에 들어왔으면 서버가 그대로 응답 DTO 및 STOMP 브로드캐스트 payload 에 에코해서
     * 낙관적 UI 가 자기 self-echo 를 매칭할 수 있게 해 준다.
     */
    val clientMsgId: String? = null
) {
    companion object {
        fun from(
            chatMessage: ChatMessage,
            fileAttachment: FileAttachment? = null,
            emoticon: Emoticon? = null,
            replyToFileAttachment: FileAttachment? = null,
            replyToEmoticon: Emoticon? = null,
            resolvedFileUrl: String? = null,
            resolvedReplyToFileUrl: String? = null,
            variantResolver: FileAttachmentLookupService? = null,
            clientMsgId: String? = null
        ): ChatMessageResponse {
            val isDeleted = chatMessage.deletedAt != null
            return ChatMessageResponse(
                id = chatMessage.id,
                roomId = chatMessage.chatRoom.id,
                senderId = chatMessage.sender?.id,
                senderNickname = chatMessage.sender?.nickname,
                messageType = chatMessage.messageType,
                textContent = if (isDeleted) null else chatMessage.textContent,
                fileId = chatMessage.fileId,
                file = fileAttachment?.let {
                    FileInfo(
                        id = it.id,
                        url = resolvedFileUrl ?: it.url,
                        urlThumbnail = it.thumbnailUrl,
                        urlMedium = it.mediumUrl,
                        urlViewer = it.viewerUrl,
                        fileType = it.fileType,
                        mimeType = it.mimeType,
                        originalFileName = it.originalFileName,
                        sizeBytes = it.sizeBytes,
                        width = it.width,
                        height = it.height
                    )
                },
                emoticonId = chatMessage.emoticonId,
                emoticonImageUrl = emoticon?.imageUrl,
                emoticonImageUrlThumbnail = variantResolver?.thumbnailUrlFor(emoticon?.imageUrl),
                emoticonImageUrlViewer = variantResolver?.viewerUrlFor(emoticon?.imageUrl),
                emoticonName = emoticon?.name,
                emojiCode = chatMessage.emojiCode,
                createdAt = chatMessage.createdAt,
                unreadCount = 0,
                replyToId = chatMessage.replyTo?.id,
                replyToSenderNickname = chatMessage.replyTo?.sender?.nickname,
                replyToTextContent = chatMessage.replyTo?.textContent,
                replyToMessageType = chatMessage.replyTo?.messageType,
                replyToFileUrl = resolvedReplyToFileUrl ?: replyToFileAttachment?.url,
                replyToFileName = replyToFileAttachment?.originalFileName,
                replyToEmoticonImageUrl = replyToEmoticon?.imageUrl,
                replyToEmoticonImageUrlThumbnail = variantResolver?.thumbnailUrlFor(replyToEmoticon?.imageUrl),
                replyToEmoticonImageUrlViewer = variantResolver?.viewerUrlFor(replyToEmoticon?.imageUrl),
                deletedAt = chatMessage.deletedAt,
                clientMsgId = clientMsgId
            )
        }

        fun from(
            chatMessage: ChatMessage,
            participants: List<ChatRoomParticipant>,
            fileAttachment: FileAttachment? = null,
            emoticon: Emoticon? = null,
            replyToFileAttachment: FileAttachment? = null,
            replyToEmoticon: Emoticon? = null,
            resolvedFileUrl: String? = null,
            resolvedReplyToFileUrl: String? = null,
            variantResolver: FileAttachmentLookupService? = null,
            clientMsgId: String? = null
        ): ChatMessageResponse {
            val computedUnreadCount = computeUnreadCount(chatMessage, participants)
            val isDeleted = chatMessage.deletedAt != null
            return ChatMessageResponse(
                id = chatMessage.id,
                roomId = chatMessage.chatRoom.id,
                senderId = chatMessage.sender?.id,
                senderNickname = chatMessage.sender?.nickname,
                messageType = chatMessage.messageType,
                textContent = if (isDeleted) null else chatMessage.textContent,
                fileId = chatMessage.fileId,
                file = fileAttachment?.let {
                    FileInfo(
                        id = it.id,
                        url = resolvedFileUrl ?: it.url,
                        urlThumbnail = it.thumbnailUrl,
                        urlMedium = it.mediumUrl,
                        urlViewer = it.viewerUrl,
                        fileType = it.fileType,
                        mimeType = it.mimeType,
                        originalFileName = it.originalFileName,
                        sizeBytes = it.sizeBytes,
                        width = it.width,
                        height = it.height
                    )
                },
                emoticonId = chatMessage.emoticonId,
                emoticonImageUrl = emoticon?.imageUrl,
                emoticonImageUrlThumbnail = variantResolver?.thumbnailUrlFor(emoticon?.imageUrl),
                emoticonImageUrlViewer = variantResolver?.viewerUrlFor(emoticon?.imageUrl),
                emoticonName = emoticon?.name,
                emojiCode = chatMessage.emojiCode,
                createdAt = chatMessage.createdAt,
                unreadCount = computedUnreadCount,
                replyToId = chatMessage.replyTo?.id,
                replyToSenderNickname = chatMessage.replyTo?.sender?.nickname,
                replyToTextContent = chatMessage.replyTo?.textContent,
                replyToMessageType = chatMessage.replyTo?.messageType,
                replyToFileUrl = resolvedReplyToFileUrl ?: replyToFileAttachment?.url,
                replyToFileName = replyToFileAttachment?.originalFileName,
                replyToEmoticonImageUrl = replyToEmoticon?.imageUrl,
                replyToEmoticonImageUrlThumbnail = variantResolver?.thumbnailUrlFor(replyToEmoticon?.imageUrl),
                replyToEmoticonImageUrlViewer = variantResolver?.viewerUrlFor(replyToEmoticon?.imageUrl),
                deletedAt = chatMessage.deletedAt,
                clientMsgId = clientMsgId
            )
        }

        fun computeUnreadCount(message: ChatMessage, participants: List<ChatRoomParticipant>): Int {
            val sender = message.sender ?: return 0
            return participants.count { p ->
                p.user.id != sender.id &&
                if (p.leftAt == null) {
                    p.lastReadAt == null || p.lastReadAt!! < message.createdAt
                } else {
                    message.createdAt <= p.leftAt!! &&
                    (p.lastReadAt == null || p.lastReadAt!! < message.createdAt)
                }
            }
        }
    }
}
