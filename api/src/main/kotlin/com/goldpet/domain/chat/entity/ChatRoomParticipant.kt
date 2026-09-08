package com.goldpet.domain.chat.entity


import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

enum class ChatRoomRole {
    OWNER, MEMBER
}

@Entity
@Table(name = "chat_room_participants", uniqueConstraints = [UniqueConstraint(columnNames = ["room_id", "user_id"])])
class ChatRoomParticipant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    val chatRoom: ChatRoom,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: ChatRoomRole,

    @Column(nullable = false)
    val joinedAt: LocalDateTime = LocalDateTime.now(),

    @Column
    var leftAt: LocalDateTime? = null,

    @Column
    var lastReadAt: LocalDateTime? = null
)
