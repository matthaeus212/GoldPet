package com.goldpet.domain.friend.service

import com.goldpet.domain.common.exception.*
import com.goldpet.domain.friend.dto.LikeResponse
import com.goldpet.domain.friend.entity.Like
import com.goldpet.domain.friend.entity.LikeStatus
import com.goldpet.domain.friend.entity.Match
import com.goldpet.domain.friend.event.LikeNotificationEvent
import com.goldpet.domain.friend.repository.LikeRepository
import com.goldpet.domain.friend.repository.MatchRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.metrics.entity.LikeEventAction
import com.goldpet.domain.metrics.service.LikeEventRecorder
import com.goldpet.domain.notification.dto.CreateNotificationRequest
import com.goldpet.domain.notification.entity.NotificationType
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class LikeService(
    private val likeRepository: LikeRepository,
    private val matchRepository: MatchRepository,
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val userBlockRepository: UserBlockRepository,
    private val likeEventRecorder: LikeEventRecorder
) {
    /**
     * Like a user. Creates new like or reactivates canceled one.
     * Sends notification to target user.
     * Returns mutual status (true if target also likes me).
     *
     * @param source 좋아요가 발생한 리스트/정렬 출처(compatible|distance|popular 등) — like_events 기록용. nullable.
     */
    fun likeUser(fromUserId: Long, toUserId: Long, source: String? = null): LikeResponse {
        val fromUser = userRepository.findById(fromUserId).orElseThrow {
            NotFoundException("User not found: $fromUserId")
        }
        val toUser = userRepository.findById(toUserId).orElseThrow {
            NotFoundException("User not found: $toUserId")
        }

        // Bidirectional block check
        if (userBlockRepository.existsByBlockerAndBlocked(fromUser, toUser)
            || userBlockRepository.existsByBlockerAndBlocked(toUser, fromUser)) {
            throw ForbiddenException("Blocked user")
        }

        // Find existing like or create new
        val existingLike = likeRepository.findByFromUserAndToUser(fromUser, toUser)
        val like = if (existingLike.isPresent) {
            existingLike.get().apply { status = LikeStatus.ACTIVE }
        } else {
            likeRepository.save(Like(fromUser = fromUser, toUser = toUser, status = LikeStatus.ACTIVE))
        }
        
        // Check if mutual (target already liked me)
        val isMutual = likeRepository.existsByFromUserIdAndToUserIdAndStatus(toUserId, fromUserId, LikeStatus.ACTIVE)

        // If mutual, create match if not exists
        if (isMutual) {
            val user1 = if (fromUser.id < toUser.id) fromUser else toUser
            val user2 = if (fromUser.id < toUser.id) toUser else fromUser

            if (matchRepository.findByUser1AndUser2(user1, user2).isEmpty) {
                matchRepository.save(Match(user1 = user1, user2 = user2))
            }
        }

        // Send notification to target
        val message = if (isMutual) {
            "${fromUser.nickname ?: "누군가"}님과 서로 좋아해가 되었어요! 💕"
        } else {
            "${fromUser.nickname ?: "누군가"}님이 좋아요를 눌렀어요! ❤️"
        }
        
        // PERF-006: FCM 발송을 트랜잭션 커밋 후 비동기로 분리(LikeNotificationEventListener).
        // 메시지 문구는 여기서 완성(발신자 닉네임 이미 로드) → 타이밍만 바뀌고 내용 불변.
        eventPublisher.publishEvent(LikeNotificationEvent(CreateNotificationRequest(
            userId = toUserId,
            type = if (isMutual) NotificationType.MATCH else NotificationType.LIKE,
            title = if (isMutual) "새로운 매칭" else "새로운 좋아요",
            message = message,
            targetId = fromUserId,
            targetType = "USER",
            senderId = fromUserId
        )))

        // like_events append (W1(2), V80): point-in-time 퍼널 사실. is_match = THIS 좋아요가 지금 매치를 만들었는지(=isMutual).
        // cohort 는 recorder 가 experiment_assignment 에서 read(재계산 금지). ACTIVE seam.
        likeEventRecorder.record(fromUserId, toUserId, LikeEventAction.LIKE, isMatch = isMutual, source = source)

        return LikeResponse(
            likeId = like.id,
            toUserId = toUserId,
            isMutual = isMutual
        )
    }
    
    /**
     * Unlike/dislike a user. Sets status to CANCELED.
     *
     * @param source 취소가 발생한 리스트/정렬 출처 — like_events 기록용. nullable.
     */
    fun unlikeUser(fromUserId: Long, toUserId: Long, source: String? = null): Boolean {
        val fromUser = userRepository.findById(fromUserId).orElse(null) ?: return false
        val toUser = userRepository.findById(toUserId).orElse(null) ?: return false
        
        val existingLike = likeRepository.findByFromUserAndToUser(fromUser, toUser)
        if (existingLike.isPresent) {
            val like = existingLike.get()
            like.status = LikeStatus.CANCELED
            likeRepository.save(like)

            // If mutual, also cancel the other side (clean break -> return to friend find)
            val reverseLike = likeRepository.findByFromUserAndToUser(toUser, fromUser)
            if (reverseLike.isPresent && reverseLike.get().status == LikeStatus.ACTIVE) {
                val rLike = reverseLike.get()
                rLike.status = LikeStatus.CANCELED
                likeRepository.save(rLike)
            }

            // Delete match if exists
            matchRepository.findByUserIds(fromUserId, toUserId).ifPresent { match ->
                matchRepository.delete(match)
            }

            // like_events append (W1(2), V80): CANCEL 은 매치를 만들지 않으므로 is_match=false. ACTIVE seam.
            likeEventRecorder.record(fromUserId, toUserId, LikeEventAction.CANCEL, isMatch = false, source = source)

            return true
        }
        return false
    }

    /**
     * Reject a received like. Sets sender's like status to CANCELED.
     */
    fun rejectLike(userId: Long, senderId: Long): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        val sender = userRepository.findById(senderId).orElse(null) ?: return false
        
        // Find the like sent BY sender TO me
        val like = likeRepository.findByFromUserAndToUser(sender, user)
        if (like.isPresent) {
            val l = like.get()
            l.status = LikeStatus.CANCELED
            likeRepository.save(l)
            return true
        }
        return false
    }
    
    /**
     * Get users I liked ("내가 좋아해")
     */
    @Transactional(readOnly = true)
    fun getMyLikes(userId: Long): List<User> {
        val likes = likeRepository.findSentOnlyLikes(userId)
        return likes.map { it.toUser }
    }
    
    /**
     * Get mutual likes ("서로 좋아해")
     */
    @Transactional(readOnly = true)
    fun getMutualLikes(userId: Long): List<User> {
        val mutualLikes = likeRepository.findMutualLikes(userId)
        return mutualLikes.map { it.toUser }
    }

    /**
     * Get likes I received ("나를 좋아해")
     */
    @Transactional(readOnly = true)
    fun getReceivedLikes(userId: Long): List<User> {
        val likes = likeRepository.findReceivedOnlyLikes(userId)
        return likes.map { it.fromUser }
    }
    
    /**
     * Check if I already liked this user
     */
    @Transactional(readOnly = true)
    fun hasLiked(fromUserId: Long, toUserId: Long): Boolean {
        return likeRepository.existsByFromUserIdAndToUserIdAndStatus(fromUserId, toUserId, LikeStatus.ACTIVE)
    }
}
