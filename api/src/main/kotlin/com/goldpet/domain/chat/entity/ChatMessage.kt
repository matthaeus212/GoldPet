package com.goldpet.domain.chat.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

enum class MessageType {
    TEXT, IMAGE, VIDEO, FILE, EMOTICON, EMOJI, SYSTEM
}

@Entity
@Table(name = "chat_messages")
class ChatMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    val chatRoom: ChatRoom,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    val sender: User? = null, // System messages might not have a sender

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val messageType: MessageType,

    @Column(columnDefinition = "TEXT")
    var textContent: String? = null,

    @Column(name = "file_id")
    var fileId: Long? = null, // Assuming file_attachments table exists

    @Column(name = "emoticon_id")
    var emoticonId: Long? = null, // Assuming emoticons table exists

    @Column
    var emojiCode: String? = null,

    @Column(nullable = false)
    var readCount: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    val replyTo: ChatMessage? = null,

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,

    /** V68 — admin/자동 신고 처리 hide 시점. deletedAt (사용자 삭제) 와 의미 분리. */
    @Column(name = "hidden_at")
    var hiddenAt: LocalDateTime? = null,

    /** V68 — hide 사유 (자동 제재 신고 누적 수 등). */
    @Column(name = "hidden_reason", length = 64)
    var hiddenReason: String? = null

) : BaseTimeEntity() {
    fun softDelete() {
        this.deletedAt = LocalDateTime.now()
        // DO NOT clear textContent - preserve for reply chain references and audit trail
    }

    /** V68 — admin/자동 신고 처리 hide. */
    fun hide(reason: String) {
        this.hiddenAt = LocalDateTime.now()
        this.hiddenReason = reason
    }
}
