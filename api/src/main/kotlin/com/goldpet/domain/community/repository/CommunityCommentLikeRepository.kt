package com.goldpet.domain.community.repository

import com.goldpet.domain.community.entity.CommunityComment
import com.goldpet.domain.community.entity.CommunityCommentLike
import com.goldpet.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface CommunityCommentLikeRepository : JpaRepository<CommunityCommentLike, Long> {
    fun findByCommentAndUser(comment: CommunityComment, user: User): CommunityCommentLike?
    fun existsByCommentAndUser(comment: CommunityComment, user: User): Boolean
    fun deleteAllByComment(comment: CommunityComment)
    fun deleteAllByCommentIn(comments: List<CommunityComment>)
}
