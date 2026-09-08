package com.goldpet.domain.notification.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*

enum class NotificationType {
    MATCH,      // 매칭 성공
    MESSAGE,    // 새 메시지
    LIKE,       // 좋아요 받음
    COMMENT,    // 댓글 알림
    FOLLOW,     // 팔로우 알림
    WALK,       // 산책 알림
    NOTICE,          // 공지사항
    EVENT,           // 이벤트
    SYSTEM,          // 시스템 알림
    REPORT_RESOLVED, // 신고 처리 결과 알림 (resolve/dismiss 모두 사용)
    REENGAGEMENT     // 재참여 넛지(휴면/스트릭-위기) — 전용 동의 카테고리 (W2c)
}

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: NotificationType,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val message: String,

    @Column(name = "target_id")
    val targetId: Long? = null,

    @Column(name = "target_type")
    val targetType: String? = null,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "sender_id")
    val senderId: Long? = null,

    @Column(name = "sender_nickname")
    val senderNickname: String? = null,

    @Column(name = "sender_profile_image")
    val senderProfileImage: String? = null

) : BaseTimeEntity()
