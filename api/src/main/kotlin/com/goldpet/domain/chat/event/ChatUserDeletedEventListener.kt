package com.goldpet.domain.chat.event

import com.goldpet.domain.chat.repository.ChatRoomParticipantRepository
import com.goldpet.domain.user.event.UserDeletedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class ChatUserDeletedEventListener(
    private val chatRoomParticipantRepository: ChatRoomParticipantRepository
) {
    private val log = LoggerFactory.getLogger(ChatUserDeletedEventListener::class.java)

    @EventListener
    @Transactional
    fun handleUserDeleted(event: UserDeletedEvent) {
        log.info("Processing user deletion for chat domain: userId={}", event.userId)

        // Mark user as having left all chat rooms
        val participants = chatRoomParticipantRepository.findAllByUserId(event.userId)
        val now = LocalDateTime.now()
        participants.forEach { participant ->
            if (participant.leftAt == null) {
                participant.leftAt = now
                chatRoomParticipantRepository.save(participant)
            }
        }

        log.info("Chat domain cleanup complete for userId={}: {} room(s) left", event.userId, participants.size)
    }
}
