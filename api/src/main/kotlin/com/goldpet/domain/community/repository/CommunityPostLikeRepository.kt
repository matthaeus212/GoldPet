package com.goldpet.domain.community.repository

import com.goldpet.domain.community.entity.CommunityPost
import com.goldpet.domain.community.entity.CommunityPostLike
import com.goldpet.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommunityPostLikeRepository : JpaRepository<CommunityPostLike, Long> {
    fun existsByPostAndUser(post: CommunityPost, user: User): Boolean
    fun findByPostAndUser(post: CommunityPost, user: User): CommunityPostLike?
    fun countByPostId(postId: Long): Long

    // PERF-004: 피드 배치 집계 — 게시글별 내 좋아요여부를 postIds IN 1회로.
    @Query("SELECT l.post.id FROM CommunityPostLike l WHERE l.post.id IN :postIds AND l.user.id = :userId")
    fun findLikedPostIds(@Param("postIds") postIds: Collection<Long>, @Param("userId") userId: Long): List<Long>
}

