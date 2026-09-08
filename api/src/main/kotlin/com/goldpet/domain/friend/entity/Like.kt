package com.goldpet.domain.friend.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*

enum class LikeStatus {
    ACTIVE, CANCELED
}

@Entity
@Table(name = "likes", uniqueConstraints = [UniqueConstraint(columnNames = ["from_user_id", "to_user_id"])])
class Like(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    val fromUser: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    val toUser: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: LikeStatus = LikeStatus.ACTIVE

) : BaseTimeEntity()
