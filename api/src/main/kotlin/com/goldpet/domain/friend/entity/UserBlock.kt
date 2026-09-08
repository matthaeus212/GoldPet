package com.goldpet.domain.friend.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*

@Entity
@Table(name = "user_blocks", uniqueConstraints = [UniqueConstraint(columnNames = ["blocker_id", "blocked_id"])])
class UserBlock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    val blocker: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    val blocked: User

) : BaseTimeEntity()
