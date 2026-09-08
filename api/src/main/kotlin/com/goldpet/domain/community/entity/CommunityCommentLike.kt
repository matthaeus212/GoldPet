package com.goldpet.domain.community.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*

@Entity
@Table(name = "community_comment_likes", uniqueConstraints = [
    UniqueConstraint(columnNames = ["comment_id", "user_id"])
])
class CommunityCommentLike(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    val comment: CommunityComment,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User
) : BaseTimeEntity()
