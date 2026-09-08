package com.goldpet.domain.community.event

import com.goldpet.domain.user.event.UserDeletedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handles user deletion events for the community domain.
 *
 * Since CommunityPost and CommunityComment use @ManyToOne(User) relationships,
 * the anonymized nickname ("탈퇴한 사용자") is resolved automatically through
 * the User entity after PII anonymization. No denormalized fields need updating.
 *
 * This listener exists as a hook for any future denormalization or additional
 * cleanup that may be required.
 */
@Component
class CommunityUserDeletedEventListener {
    private val log = LoggerFactory.getLogger(CommunityUserDeletedEventListener::class.java)

    @EventListener
    @Transactional
    fun handleUserDeleted(event: UserDeletedEvent) {
        log.info("Processing user deletion for community domain: userId={}", event.userId)
        // Posts and comments automatically show anonymized nickname via User relationship.
        // No denormalized fields require explicit updates.
        log.info("Community domain cleanup complete for userId={}", event.userId)
    }
}
