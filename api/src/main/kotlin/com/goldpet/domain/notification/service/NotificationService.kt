package com.goldpet.domain.notification.service

import com.goldpet.domain.notification.dto.CreateNotificationRequest
import com.goldpet.domain.notification.dto.NotificationResponse
import com.goldpet.domain.notification.entity.Notification
import com.goldpet.domain.notification.entity.NotificationType
import com.goldpet.domain.notification.repository.NotificationRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.user.repository.UserDeviceRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val fcmPushSender: FcmPushSender
) {
    fun getNotifications(userId: Long, category: String?, pageable: Pageable): Page<NotificationResponse> {
        if (category == null || category == "ALL") {
            return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map { NotificationResponse.from(it) }
        }

        val types = when (category) {
            "FRIEND" -> listOf(NotificationType.LIKE, NotificationType.MATCH)
            "CHAT" -> listOf(NotificationType.MESSAGE)
            "WALK" -> listOf(NotificationType.WALK)
            "COMMUNITY" -> listOf(NotificationType.COMMENT, NotificationType.FOLLOW)
            "NOTICE" -> listOf(NotificationType.NOTICE, NotificationType.SYSTEM)
            "EVENT" -> listOf(NotificationType.EVENT)
            else -> return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map { NotificationResponse.from(it) }
        }

        return notificationRepository.findAllByUserIdAndTypeInOrderByCreatedAtDesc(userId, types, pageable)
            .map { NotificationResponse.from(it) }
    }

    fun getUnreadCount(userId: Long): Long {
        return notificationRepository.countByUserIdAndIsReadFalse(userId)
    }

    @Transactional
    fun markAsRead(notificationId: Long, userId: Long) {
        val notification = notificationRepository.findById(notificationId)
            .orElseThrow { NotFoundException("Notification not found") }

        if (notification.user.id != userId) {
            throw ForbiddenException("Not authorized")
        }

        notification.isRead = true
        notificationRepository.save(notification)
    }

    @Transactional
    fun markAllAsRead(userId: Long): Int {
        return notificationRepository.markAllAsRead(userId)
    }

    @Transactional
    fun deleteNotification(notificationId: Long, userId: Long) {
        val notification = notificationRepository.findById(notificationId)
            .orElseThrow { NotFoundException("Notification not found") }

        if (notification.user.id != userId) {
            throw ForbiddenException("Not authorized")
        }
        
        notificationRepository.delete(notification)
    }

    @Transactional
    fun createNotification(request: CreateNotificationRequest): NotificationResponse {
        val user = userRepository.findById(request.userId)
            .orElseThrow { NotFoundException("User not found") }
        
        val sender = request.senderId?.let { userRepository.findById(it).orElse(null) }
        
        val notification = Notification(
            user = user,
            type = request.type,
            title = request.title,
            message = request.message,
            targetId = request.targetId,
            targetType = request.targetType,
            senderId = sender?.id,
            senderNickname = sender?.nickname,
            senderProfileImage = sender?.profileImageUrl
        )
        
        val saved = notificationRepository.save(notification)

        // Send FCM push to all active devices if notifications enabled
        if (user.isNotificationEnabled && isCategoryEnabled(user, request.type)) {
            val pushData = buildMap<String, String> {
                request.targetId?.let { put("targetId", it.toString()) }
                request.targetType?.let { put("targetType", it) }
                put("type", request.type.name)
                put("notificationId", saved.id.toString())
            }
            val activeDevices = userDeviceRepository.findAllByUserIdAndIsActiveTrue(request.userId)
            val fcmTokens = activeDevices.mapNotNull { it.fcmToken }.distinct()
            // Sprint 4 BLOCKER #8 — detailed 메서드로 invalid token 추출 후 deactivate.
            when {
                fcmTokens.size == 1 -> {
                    val token = fcmTokens.first()
                    val result = fcmPushSender.sendDetailed(
                        fcmToken = token,
                        title = request.title,
                        message = request.message,
                        data = pushData,
                    )
                    if (result is FcmSendResult.InvalidToken) {
                        userDeviceRepository.deactivateByFcmToken(token)
                    }
                }
                fcmTokens.size > 1 -> {
                    val multicast = fcmPushSender.sendToMultipleDetailed(
                        fcmTokens = fcmTokens,
                        title = request.title,
                        message = request.message,
                        data = pushData,
                    )
                    multicast.invalidTokens.forEach { invalid ->
                        userDeviceRepository.deactivateByFcmToken(invalid)
                    }
                }
                // No tokens: skip push, notification already saved
            }
        }

        return NotificationResponse.from(saved)
    }

    private fun isCategoryEnabled(user: User, type: NotificationType): Boolean {
        return when (type) {
            NotificationType.MESSAGE -> user.isChatAlertEnabled
            NotificationType.COMMENT, NotificationType.FOLLOW -> user.isCommunityAlertEnabled
            NotificationType.NOTICE, NotificationType.EVENT -> user.isMarketingAlertEnabled
            // W2c: 재참여 넛지는 전용 동의 카테고리. WALK 의 else->true 상시 레인 사용 금지(동의 우회 방지).
            NotificationType.REENGAGEMENT -> user.isReengagementAlertEnabled
            else -> true // MATCH, LIKE, WALK, SYSTEM - always allowed
        }
    }

    // 편의 메서드: 특정 이벤트에 대한 알림 생성
    @Transactional
    fun notifyMatch(toUserId: Long, fromUserId: Long, matchId: Long) {
        val fromUser = userRepository.findById(fromUserId).orElse(null) ?: return
        createNotification(CreateNotificationRequest(
            userId = toUserId,
            type = NotificationType.MATCH,
            title = "새로운 매칭!",
            message = "${fromUser.nickname}님과 매칭되었어요 🎉",
            targetId = matchId,
            targetType = "MATCH",
            senderId = fromUserId
        ))
    }

    @Transactional
    fun notifyLike(toUserId: Long, fromUserId: Long, postId: Long) {
        val fromUser = userRepository.findById(fromUserId).orElse(null) ?: return
        createNotification(CreateNotificationRequest(
            userId = toUserId,
            type = NotificationType.LIKE,
            title = "좋아요!",
            message = "${fromUser.nickname}님이 게시글을 좋아해요 ❤️",
            targetId = postId,
            targetType = "POST",
            senderId = fromUserId
        ))
    }

    @Transactional
    fun notifyComment(toUserId: Long, fromUserId: Long, postId: Long, commentPreview: String) {
        val fromUser = userRepository.findById(fromUserId).orElse(null) ?: return
        createNotification(CreateNotificationRequest(
            userId = toUserId,
            type = NotificationType.COMMENT,
            title = "새 댓글",
            message = "${fromUser.nickname}: ${commentPreview.take(30)}...",
            targetId = postId,
            targetType = "POST",
            senderId = fromUserId
        ))
    }

    @Transactional
    fun notifyMessage(toUserId: Long, fromUserId: Long, chatRoomId: Long, messagePreview: String) {
        val fromUser = userRepository.findById(fromUserId).orElse(null) ?: return
        createNotification(CreateNotificationRequest(
            userId = toUserId,
            type = NotificationType.MESSAGE,
            title = "새 메시지",
            message = "${fromUser.nickname}: ${messagePreview.take(30)}...",
            targetId = chatRoomId,
            targetType = "CHAT_ROOM",
            senderId = fromUserId
        ))
    }

    /**
     * 산책 완료 보상-요약 푸시 (해석 A). 사용자의 모든 활성 기기로 fan-out (createNotification 단일 경로).
     * 보상이 실제 적립된 경우에만 의미가 있으므로 goldReward > 0 일 때만 호출하는 것을 전제.
     *
     * W2a: walk-완료 + streak 를 단일 푸시로 병합(한 산책=한 푸시). [streakDays] 가 2 이상이면
     * 연속 산책 문구를 본문 뒤에 덧붙인다(별도 streak 푸시 금지 — 중복 방지).
     */
    @Transactional
    fun notifyWalkCompleted(userId: Long, walkId: Long, distanceKm: Double, goldReward: Int, streakDays: Int? = null) {
        val base = "${String.format("%.1f", distanceKm)}km 완주! +${goldReward} 골드 적립"
        val message = if (streakDays != null && streakDays >= 2) "$base · 🔥${streakDays}일 연속 산책!" else base
        createNotification(CreateNotificationRequest(
            userId = userId,
            type = NotificationType.WALK,
            title = "산책 완료 🐾",
            message = message,
            targetId = walkId,
            targetType = "WALK",
            senderId = null
        ))
    }

    @Transactional
    fun notifyReportResolved(toUserId: Long, actionLabel: String) {
        createNotification(CreateNotificationRequest(
            userId = toUserId,
            type = NotificationType.REPORT_RESOLVED,
            title = "신고 처리 완료",
            message = "회원님의 신고가 처리되었습니다. 조치: $actionLabel",
            targetId = null,
            targetType = "REPORT",
            senderId = null
        ))
    }

    @Transactional
    fun notifyReportDismissed(toUserId: Long) {
        createNotification(CreateNotificationRequest(
            userId = toUserId,
            type = NotificationType.REPORT_RESOLVED,
            title = "신고 검토 완료",
            message = "회원님의 신고가 검토 후 기각되었습니다",
            targetId = null,
            targetType = "REPORT",
            senderId = null
        ))
    }
}
