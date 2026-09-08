package com.goldpet.domain.chat.service

import com.goldpet.domain.chat.dto.ChatRoomCreateRequest
import com.goldpet.domain.chat.dto.ChatMessageRequest
import com.goldpet.domain.chat.dto.ChatMessageResponse
import com.goldpet.domain.chat.entity.*
import com.goldpet.domain.chat.event.ChatMessageSavedEvent
import com.goldpet.domain.chat.repository.ChatMessageRepository
import com.goldpet.domain.chat.repository.ChatRequestRepository
import com.goldpet.domain.chat.repository.ChatRoomParticipantRepository
import com.goldpet.domain.chat.repository.ChatRoomRepository
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.friend.repository.MatchRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.notification.service.NotificationService
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ChatService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomParticipantRepository: ChatRoomParticipantRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val userRepository: UserRepository,
    private val matchRepository: MatchRepository,
    private val notificationService: NotificationService,
    private val userBlockRepository: UserBlockRepository,
    private val chatRequestRepository: ChatRequestRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val chatMessageAssembler: ChatMessageAssembler,
    @Value("\${goldpet.chat.async.enabled:false}") private val asyncNotificationEnabled: Boolean,
) {
    /** 발신 결과 — 조립된 응답과, 목록 갱신 알림을 보낼 참여자 id. 브로드캐스트는 커밋 후 컨트롤러가 한다. */
    data class SentMessage(
        val response: ChatMessageResponse,
        val participantUserIds: List<Long>
    )

    /** 읽음 처리로 unreadCount 가 바뀐 메시지. WS payload 로 그대로 직렬화된다. */
    data class ReadUpdate(
        val messageId: Long,
        val unreadCount: Int
    )

    @Transactional
    fun createChatRoom(request: ChatRoomCreateRequest, creatorId: Long): ChatRoom {
        val creator = userRepository.findById(creatorId).orElseThrow { NotFoundException("User not found with id: $creatorId") }

        // Handle DIRECT chat room creation
        if (request.roomType == ChatRoomType.DIRECT) {
            val targetUserId = request.participantUserIds.firstOrNull()
                ?: throw BadRequestException("Direct chat room requires a target user ID.")
            val targetUser = userRepository.findById(targetUserId).orElseThrow { NotFoundException("Target user not found with id: $targetUserId") }

            // Find existing match
            val match = request.matchId?.let { matchRepository.findById(it).orElse(null) }
                ?: throw BadRequestException("Direct chat room requires a valid match ID.")

            // Check if a direct chat room for this match already exists
            chatRoomRepository.findByMatchId(match.id).ifPresent {
                throw ConflictException("Direct chat room for match ${match.id} already exists.")
            }

            val chatRoom = ChatRoom(
                roomType = ChatRoomType.DIRECT,
                ownerUser = creator, // Owner can be the creator for direct chat
                match = match
            )
            chatRoomRepository.save(chatRoom)

            // Add participants
            chatRoomParticipantRepository.save(ChatRoomParticipant(chatRoom = chatRoom, user = creator, role = ChatRoomRole.MEMBER))
            chatRoomParticipantRepository.save(ChatRoomParticipant(chatRoom = chatRoom, user = targetUser, role = ChatRoomRole.MEMBER))

            return chatRoom
        } else {
            // Handle GROUP or AI_PET chat room creation
            val chatRoom = ChatRoom(
                roomType = request.roomType,
                title = request.title,
                ownerUser = creator
            )
            chatRoomRepository.save(chatRoom)

            // Add creator as owner/member
            chatRoomParticipantRepository.save(ChatRoomParticipant(chatRoom = chatRoom, user = creator, role = ChatRoomRole.OWNER))

            // Add other participants for GROUP chat
            if (request.roomType == ChatRoomType.GROUP) {
                request.participantUserIds.forEach { userId ->
                    val user = userRepository.findById(userId).orElseThrow { NotFoundException("Participant user not found with id: $userId") }
                    chatRoomParticipantRepository.save(ChatRoomParticipant(chatRoom = chatRoom, user = user, role = ChatRoomRole.MEMBER))
                }
            }
            return chatRoom
        }
    }

    @Transactional
    fun addParticipant(roomId: Long, userId: Long, role: ChatRoomRole = ChatRoomRole.MEMBER): ChatRoomParticipant {
        val chatRoom = chatRoomRepository.findById(roomId).orElseThrow { NotFoundException("Chat room not found with id: $roomId") }
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }

        if (chatRoomParticipantRepository.findByChatRoomIdAndUserId(roomId, userId).isPresent) {
            throw ConflictException("User ${user.nickname} is already a participant in chat room ${chatRoom.title}.")
        }

        val participant = ChatRoomParticipant(chatRoom = chatRoom, user = user, role = role)
        return chatRoomParticipantRepository.save(participant)
    }

    @Transactional
    fun inviteParticipant(roomId: Long, requesterId: Long, inviteeId: Long): ChatRoom {
        val chatRoom = chatRoomRepository.findById(roomId)
            .orElseThrow { NotFoundException("Chat room not found with id: $roomId") }

        // AI_PET rooms cannot have invited participants
        if (chatRoom.roomType == ChatRoomType.AI_PET) {
            throw BadRequestException("Cannot invite to AI pet chat")
        }

        // Verify requester is an active participant
        // 라운드3 리뷰 MEDIUM(leftAt): 나간 요청자(leftAt≠null)는 초대 불가 — 접근 인가 검증이므로 필터 적용.
        // (미필터 시 나간 사용자가 타인 초대 + DIRECT→GROUP 전환으로 OWNER 승격/소유권 탈취 가능했음)
        val requesterParticipant = chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(roomId, requesterId)
            .orElseThrow { ForbiddenException("User $requesterId is not a participant of chat room $roomId") }

        // Convert DIRECT to GROUP if needed
        if (chatRoom.roomType == ChatRoomType.DIRECT) {
            convertDirectToGroup(chatRoom, requesterParticipant)
        }

        addParticipant(roomId, inviteeId)
        return chatRoom
    }

    private fun convertDirectToGroup(chatRoom: ChatRoom, requesterParticipant: ChatRoomParticipant) {
        val requester = requesterParticipant.user

        // Convert room type
        chatRoom.roomType = ChatRoomType.GROUP
        chatRoom.ownerUser = requester
        chatRoomRepository.save(chatRoom)

        // Promote requester to OWNER
        requesterParticipant.role = ChatRoomRole.OWNER
        chatRoomParticipantRepository.save(requesterParticipant)

        // Create system message
        val systemMessage = ChatMessage(
            chatRoom = chatRoom,
            sender = null,
            messageType = MessageType.SYSTEM,
            textContent = "그룹 채팅으로 전환되었습니다"
        )
        chatMessageRepository.save(systemMessage)
    }

    @Transactional
    fun removeParticipant(roomId: Long, userId: Long) {
        val participant = chatRoomParticipantRepository.findByChatRoomIdAndUserId(roomId, userId)
            .orElseThrow { NotFoundException("Participant not found in chat room $roomId for user $userId") }
        chatRoomParticipantRepository.delete(participant)
    }

    @Transactional
    fun saveChatMessage(request: ChatMessageRequest): ChatMessage {
        val chatRoom = chatRoomRepository.findById(request.roomId).orElseThrow { NotFoundException("Chat room not found with id: ${request.roomId}") }
        val sender = userRepository.findById(request.senderId).orElseThrow { NotFoundException("Sender user not found with id: ${request.senderId}") }

        // Validate sender is a participant if not a system message
        // 라운드2 리뷰 MEDIUM(leftAt): 나간 참여자(leftAt≠null)는 발신 불가 — 접근 인가 검증.
        if (request.messageType != MessageType.SYSTEM) {
            chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(chatRoom.id, sender.id)
                .orElseThrow { ForbiddenException("채팅방 참여자가 아닙니다", errorCode = "NOT_PARTICIPANT") }
        }

        // Block check: verify no block relationship with the other participant in 1:1 chat
        val otherParticipant = chatRoom.participants.firstOrNull { it.user.id != request.senderId }
        if (otherParticipant != null) {
            val otherId = otherParticipant.user.id
            if (userBlockRepository.existsByBlockerIdAndBlockedId(request.senderId, otherId)
                || userBlockRepository.existsByBlockerIdAndBlockedId(otherId, request.senderId)) {
                throw ForbiddenException("Cannot send message to blocked user")
            }
        }

        val replyToMessage = request.replyToId?.let { replyToId ->
            val replyMsg = chatMessageRepository.findById(replyToId)
                .orElseThrow { NotFoundException("Reply target message not found: $replyToId") }
            if (replyMsg.chatRoom.id != chatRoom.id) {
                throw com.goldpet.domain.common.exception.BadRequestException("Reply target message is not in the same chat room")
            }
            replyMsg
        }

        val chatMessage = ChatMessage(
            chatRoom = chatRoom,
            sender = sender,
            messageType = request.messageType,
            textContent = request.textContent,
            fileId = request.fileId,
            emoticonId = request.emoticonId,
            emojiCode = request.emojiCode,
            replyTo = replyToMessage
        )
        val savedMessage = chatMessageRepository.save(chatMessage)

        // T-chat-latency-v2 Step 2:
        // - `chatRoom.participants` 는 line 163 block check 에서 이미 lazy-load 완료 → `findAllByChatRoomId` 중복 쿼리 제거.
        // - async 플래그 on 시 `ChatMessageSavedEvent` 발행 → AFTER_COMMIT 에서 `chatNotificationExecutor` 에 위임.
        // - off 시 기존 inline loop 동기 실행 (fallback — prod 안정 복귀 경로).
        if (request.messageType != MessageType.SYSTEM) {
            val recipientUserIds = chatRoom.participants
                .asSequence()
                .filter { it.user.id != sender.id && it.leftAt == null }
                .map { it.user.id }
                .toList()

            if (recipientUserIds.isNotEmpty()) {
                val messagePreview = request.textContent?.takeIf { it.isNotBlank() } ?: when (request.messageType) {
                    MessageType.IMAGE -> "사진을 보냈습니다"
                    MessageType.VIDEO -> "동영상을 보냈습니다"
                    MessageType.FILE -> "파일을 보냈습니다"
                    MessageType.EMOTICON -> "이모티콘을 보냈습니다"
                    else -> "메시지를 보냈습니다"
                }

                if (asyncNotificationEnabled) {
                    applicationEventPublisher.publishEvent(
                        ChatMessageSavedEvent(
                            messageId = savedMessage.id,
                            roomId = chatRoom.id,
                            senderId = sender.id,
                            recipientUserIds = recipientUserIds,
                            messagePreview = messagePreview,
                            clientMsgId = request.clientMsgId,
                        )
                    )
                } else {
                    recipientUserIds.forEach { recipientUserId ->
                        notificationService.notifyMessage(
                            toUserId = recipientUserId,
                            fromUserId = sender.id,
                            chatRoomId = chatRoom.id,
                            messagePreview = messagePreview
                        )
                    }
                }
            }
        }

        return savedMessage
    }

    @Transactional(readOnly = true)
    fun getChatHistory(roomId: Long, viewerId: Long, page: Int, size: Int): List<ChatMessage> {
        chatRoomRepository.findById(roomId).orElseThrow { NotFoundException("Chat room not found with id: $roomId") }
        // EXT-CDX-003 (W1a): IDOR 방어 — 방 참여자만 히스토리 열람.
        // 컨트롤러 가드가 아니라 서비스 레이어에 착지(ARCH-003/W4 조립 이관 대비 seam 고정).
        // 독립 리뷰 LOW: 미참여자를 403(Forbidden)으로 구분하면 "방은 존재하지만 내가 참여자가
        // 아니다"가 노출되어 roomId enumeration 이 가능하다. 방 미존재와 동일한 404 로 통일한다.
        // 라운드2 리뷰 MEDIUM(leftAt): 나간 참여자는 이력 열람 불가 — 접근 인가 검증이므로 필터 적용.
        chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(roomId, viewerId)
            .orElseThrow { NotFoundException("Chat room not found with id: $roomId") }
        val pageable = PageRequest.of(page, size)
        // V68 BLOCKER #5 — 사용자 노출 경로: visible 메서드 (hidden_at/deleted_at IS NULL).
        return chatMessageRepository.findVisibleByChatRoomIdOrderByCreatedAtDesc(roomId, pageable).content.reversed()
    }

    /**
     * 방의 활성 참여자(나가지 않은)인지 확인한다. 아니면 false.
     *
     * ARCH-005: WS typing 핸들러가 이 검증을 위해 chatRoomParticipantRepository 를 직접
     * 주입받고 있었다. 인가 판단은 서비스 계약으로 노출한다(예외 타입은 호출 계층이 정한다 —
     * WS 는 MessageDeliveryException, REST 는 404).
     */
    @Transactional(readOnly = true)
    fun isActiveParticipant(roomId: Long, userId: Long): Boolean =
        chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(roomId, userId).isPresent

    /**
     * ARCH-003: 히스토리 조회 + 응답 조립을 서비스에서 완결한다.
     * 이전에는 컨트롤러가 참여자·첨부·이모티콘 리포지토리를 직접 주입해 조립까지 수행했다.
     */
    @Transactional(readOnly = true)
    fun getChatHistoryResponses(roomId: Long, viewerId: Long, page: Int, size: Int): List<ChatMessageResponse> {
        val messages = getChatHistory(roomId, viewerId, page, size)
        val participants = chatRoomParticipantRepository.findAllByChatRoomId(roomId)
        return chatMessageAssembler.assemble(messages, participants)
    }

    /**
     * ARCH-003: 메시지 저장 + 응답 조립. 브로드캐스트(WS)는 트랜잭션 커밋 뒤에 일어나야 하므로
     * 컨트롤러가 이 메서드 반환 후에 수행한다.
     */
    @Transactional
    fun sendMessage(request: ChatMessageRequest): SentMessage {
        val message = saveChatMessage(request)
        val participants = chatRoomParticipantRepository.findAllByChatRoomId(request.roomId)
        return SentMessage(
            response = chatMessageAssembler.assembleOne(message, participants, request.clientMsgId),
            participantUserIds = participants.map { it.user.id }
        )
    }

    /**
     * ARCH-003: 읽음 처리 후, unreadCount 가 갱신된 메시지 목록을 계산해 돌려준다.
     * 이전에는 컨트롤러가 chatMessageRepository 를 직접 뒤져 이 계산을 했다.
     */
    @Transactional
    fun markAsReadAndCollectUpdates(roomId: Long, userId: Long): List<ReadUpdate> {
        val oldLastReadAt = markAsRead(roomId, userId)

        // V68 BLOCKER #5 — 사용자 read count 갱신은 visible 메시지만 (hidden/deleted 제외).
        // 첫 read 시 oldLastReadAt=null → epoch(1970-01-01)로 통합 (모든 visible 메시지 fetch).
        // LocalDateTime.MIN 은 pgjdbc 변환 시 오버플로되어 PG "timestamp out of range" 오류가 난다.
        val since = oldLastReadAt ?: LocalDateTime.of(1970, 1, 1, 0, 0)
        val newlyRead = chatMessageRepository.findVisibleByChatRoomIdAndCreatedAtAfter(roomId, since)
        if (newlyRead.isEmpty()) return emptyList()

        // 갱신된 lastReadAt 이 반영된 참여자로 계산한다.
        val participants = chatRoomParticipantRepository.findAllByChatRoomId(roomId)
        return newlyRead.map { msg ->
            ReadUpdate(msg.id, ChatMessageResponse.computeUnreadCount(msg, participants))
        }
    }

    @Transactional(readOnly = true)
    fun getUserChatRooms(userId: Long): List<ChatRoom> {
        userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }
        val myRooms = chatRoomParticipantRepository.findAllByUserIdAndLeftAtIsNull(userId).map { it.chatRoom }
        return myRooms.filter { room ->
            val otherParticipant = room.participants.firstOrNull { it.user.id != userId }
            if (otherParticipant == null) true
            else {
                val otherId = otherParticipant.user.id
                !userBlockRepository.existsByBlockerIdAndBlockedId(userId, otherId)
                    && !userBlockRepository.existsByBlockerIdAndBlockedId(otherId, userId)
            }
        }
    }

    /**
     * Mark all messages as read for a user in a room.
     * Returns the previous lastReadAt (null if first time reading).
     * Returns the current lastReadAt if nothing new to read (caller can skip broadcast).
     */
    @Transactional
    fun markAsRead(roomId: Long, userId: Long): LocalDateTime? {
        // 라운드2 리뷰 MEDIUM(leftAt): 나간 참여자는 읽음 처리 불가 — 접근 인가 검증이므로 필터 적용.
        val participant = chatRoomParticipantRepository.findByChatRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)
            .orElseThrow { NotFoundException("Participant not found in chat room $roomId for user $userId") }

        val oldLastReadAt = participant.lastReadAt
        participant.lastReadAt = LocalDateTime.now()
        chatRoomParticipantRepository.save(participant)
        return oldLastReadAt
    }

    @Transactional
    fun leaveRoom(roomId: Long, userId: Long) {
        val participant = chatRoomParticipantRepository.findByChatRoomIdAndUserId(roomId, userId)
            .orElseThrow { NotFoundException("Participant not found in chat room $roomId for user $userId") }

        // Mark as left instead of deleting (soft delete)
        participant.leftAt = java.time.LocalDateTime.now()
        // Set lastReadAt to leftAt so left participants don't inflate unread counts
        participant.lastReadAt = participant.leftAt
        chatRoomParticipantRepository.save(participant)
    }

    /**
     * Get or create a direct chat room between two users based on their match.
     * If a chat room already exists for the match, return it.
     * Otherwise, create a new chat room.
     */
    @Transactional
    fun getOrCreateDirectRoom(userId: Long, targetUserId: Long): ChatRoom {
        // Block check
        if (userBlockRepository.existsByBlockerIdAndBlockedId(userId, targetUserId)
            || userBlockRepository.existsByBlockerIdAndBlockedId(targetUserId, userId)) {
            throw ForbiddenException("Blocked user")
        }

        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found: $userId") }
        val targetUser = userRepository.findById(targetUserId)
            .orElseThrow { NotFoundException("Target user not found: $targetUserId") }

        // Find match between the two users
        val match = matchRepository.findByUserIds(userId, targetUserId)
            .orElseThrow { BadRequestException("매칭되지 않은 사용자와는 채팅할 수 없습니다", errorCode = "NO_MATCH") }

        // Check if chat room already exists for this match
        val existingRoom = chatRoomRepository.findByMatchId(match.id)
        if (existingRoom.isPresent) {
            val room = existingRoom.get()
            // Re-activate participants who previously left
            listOf(userId, targetUserId).forEach { uid ->
                chatRoomParticipantRepository.findByChatRoomIdAndUserId(room.id, uid).ifPresent { p ->
                    if (p.leftAt != null) {
                        p.leftAt = null
                        chatRoomParticipantRepository.save(p)
                    }
                }
            }
            return room
        }

        // Create new chat room
        val chatRoom = ChatRoom(
            roomType = ChatRoomType.DIRECT,
            ownerUser = user,
            match = match
        )
        chatRoomRepository.save(chatRoom)

        // Add both users as participants
        chatRoomParticipantRepository.save(ChatRoomParticipant(chatRoom = chatRoom, user = user, role = ChatRoomRole.MEMBER))
        chatRoomParticipantRepository.save(ChatRoomParticipant(chatRoom = chatRoom, user = targetUser, role = ChatRoomRole.MEMBER))

        return chatRoom
    }

    /**
     * Get all active participants of a chat room.
     */
    @Transactional(readOnly = true)
    fun getParticipants(roomId: Long): List<ChatRoomParticipant> {
        return chatRoomParticipantRepository.findAllByChatRoomId(roomId)
            .filter { it.leftAt == null }
    }

    /**
     * Get pending and rejected chat requests for the current user (as target).
     */
    @Transactional(readOnly = true)
    fun getPendingRequests(userId: Long): List<ChatRequest> {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found: $userId") }
        return chatRequestRepository.findByTargetUser(user)
            .filter { it.status == ChatRequestStatus.PENDING || it.status == ChatRequestStatus.REJECTED }
    }

    /**
     * Accept a chat request — creates a DIRECT chat room and links both users.
     */
    @Transactional
    fun acceptRequest(requestId: Long, userId: Long): ChatRoom {
        val request = chatRequestRepository.findById(requestId)
            .orElseThrow { NotFoundException("Chat request not found: $requestId") }

        if (request.targetUser.id != userId) {
            throw ForbiddenException("Not authorized to accept this chat request")
        }
        if (request.status != ChatRequestStatus.PENDING) {
            throw BadRequestException("Chat request is not in PENDING status")
        }

        // Check if a DIRECT room already exists between the two users
        val existingRoom = chatRoomRepository.findDirectRoomBetweenUsers(request.requester.id, request.targetUser.id)
        if (existingRoom.isPresent) {
            val room = existingRoom.get()
            // Re-activate participants who previously left
            listOf(request.requester.id, request.targetUser.id).forEach { uid ->
                chatRoomParticipantRepository.findByChatRoomIdAndUserId(room.id, uid).ifPresent { p ->
                    if (p.leftAt != null) {
                        p.leftAt = null
                        chatRoomParticipantRepository.save(p)
                    }
                }
            }
            request.status = ChatRequestStatus.ACCEPTED
            request.chatRoom = room
            request.respondedAt = LocalDateTime.now()
            chatRequestRepository.save(request)
            return room
        }

        val chatRoom = ChatRoom(
            roomType = ChatRoomType.DIRECT,
            ownerUser = request.requester
        )
        chatRoomRepository.save(chatRoom)

        chatRoomParticipantRepository.save(ChatRoomParticipant(chatRoom = chatRoom, user = request.requester, role = ChatRoomRole.MEMBER))
        chatRoomParticipantRepository.save(ChatRoomParticipant(chatRoom = chatRoom, user = request.targetUser, role = ChatRoomRole.MEMBER))

        request.status = ChatRequestStatus.ACCEPTED
        request.chatRoom = chatRoom
        request.respondedAt = LocalDateTime.now()
        chatRequestRepository.save(request)

        return chatRoom
    }

    /**
     * Reject a chat request.
     */
    @Transactional
    fun rejectRequest(requestId: Long, userId: Long) {
        val request = chatRequestRepository.findById(requestId)
            .orElseThrow { NotFoundException("Chat request not found: $requestId") }

        if (request.targetUser.id != userId) {
            throw ForbiddenException("Not authorized to reject this chat request")
        }
        if (request.status != ChatRequestStatus.PENDING) {
            throw BadRequestException("Chat request is not in PENDING status")
        }

        request.status = ChatRequestStatus.REJECTED
        request.respondedAt = LocalDateTime.now()
        chatRequestRepository.save(request)
    }

    @Transactional
    fun deleteMessage(roomId: Long, messageId: Long, userId: Long) {
        val message = chatMessageRepository.findById(messageId)
            .orElseThrow { NotFoundException("Message not found") }
        if (message.chatRoom.id != roomId) {
            throw BadRequestException("Message does not belong to this room")
        }
        if (message.sender?.id != userId) {
            throw ForbiddenException("Not authorized to delete this message")
        }
        message.softDelete()
        chatMessageRepository.save(message)
    }

    /**
     * Delete a rejected chat request.
     */
    @Transactional
    fun deleteRequest(requestId: Long, userId: Long) {
        val request = chatRequestRepository.findById(requestId)
            .orElseThrow { NotFoundException("Chat request not found: $requestId") }

        if (request.targetUser.id != userId) {
            throw ForbiddenException("Not authorized to delete this chat request")
        }
        if (request.status != ChatRequestStatus.REJECTED) {
            throw BadRequestException("Only rejected chat requests can be deleted")
        }

        chatRequestRepository.delete(request)
    }
}
