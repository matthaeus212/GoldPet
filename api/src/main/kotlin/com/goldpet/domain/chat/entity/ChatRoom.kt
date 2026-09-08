package com.goldpet.domain.chat.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.friend.entity.Match
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*

enum class ChatRoomType {
    DIRECT, GROUP, AI_PET
}

@Entity
@Table(name = "chat_rooms")
class ChatRoom(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var roomType: ChatRoomType,

    @Column
    var title: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    var ownerUser: User? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    val match: Match? = null,

    @OneToMany(mappedBy = "chatRoom", cascade = [CascadeType.ALL], orphanRemoval = true)
    val participants: MutableList<ChatRoomParticipant> = mutableListOf(),

    @OneToMany(mappedBy = "chatRoom", cascade = [CascadeType.ALL], orphanRemoval = true)
    val messages: MutableList<ChatMessage> = mutableListOf()

) : BaseTimeEntity()
