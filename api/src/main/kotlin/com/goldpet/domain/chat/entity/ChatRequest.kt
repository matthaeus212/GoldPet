package com.goldpet.domain.chat.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

enum class ChatRequestStatus {
    PENDING, ACCEPTED, REJECTED
}

@Entity
@Table(name = "chat_requests")
class ChatRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    val requester: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    val targetUser: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    var chatRoom: ChatRoom? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ChatRequestStatus = ChatRequestStatus.PENDING,

    @Column
    var respondedAt: LocalDateTime? = null

) : BaseTimeEntity()
