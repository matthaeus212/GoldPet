package com.goldpet.domain.community.repository

import com.goldpet.domain.community.entity.CommunityComment
import com.goldpet.domain.community.entity.CommunityPost
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommunityCommentRepository : JpaRepository<CommunityComment, Long> {
    fun findAllByPost(post: CommunityPost, pageable: Pageable): Page<CommunityComment>
    fun findAllByPostAndIsHiddenFalse(post: CommunityPost, pageable: Pageable): Page<CommunityComment>

    @Query("SELECT c FROM CommunityComment c LEFT JOIN FETCH c.user LEFT JOIN FETCH c.parentComment WHERE c.post.id = :postId AND c.isHidden = false ORDER BY c.createdAt ASC")
    fun findByPostIdAndIsHiddenFalseOrderByCreatedAtAsc(@Param("postId") postId: Long): List<CommunityComment>

    // Admin methods
    fun findByPostIdOrderByCreatedAtAsc(postId: Long): List<CommunityComment>
    fun existsByPostAndUser(post: CommunityPost, user: com.goldpet.domain.user.entity.User): Boolean
    fun existsByPostIdAndUserId(postId: Long, userId: Long): Boolean
    fun existsByParentCommentIdAndUserId(parentCommentId: Long, userId: Long): Boolean
    fun countByPostId(postId: Long): Long

    // PERF-004: 피드 배치 집계 — 게시글별 댓글수/내 댓글여부를 postIds IN 1회로.
    @Query("SELECT c.post.id, COUNT(c) FROM CommunityComment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    fun countByPostIdIn(@Param("postIds") postIds: Collection<Long>): List<Array<Any>>

    @Query("SELECT DISTINCT c.post.id FROM CommunityComment c WHERE c.post.id IN :postIds AND c.user.id = :userId")
    fun findCommentedPostIds(@Param("postIds") postIds: Collection<Long>, @Param("userId") userId: Long): List<Long>

    fun countByUserId(userId: Long): Long
    fun findAllByUserId(userId: Long): List<CommunityComment>
    fun findAllByParentComment(parentComment: CommunityComment): List<CommunityComment>

    // User's comments with eager-loaded post (prevents N+1)
    @EntityGraph(attributePaths = ["post"])
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<CommunityComment>
}

