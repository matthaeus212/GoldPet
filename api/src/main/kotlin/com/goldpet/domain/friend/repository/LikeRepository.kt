package com.goldpet.domain.friend.repository

import com.goldpet.domain.friend.entity.Like
import com.goldpet.domain.friend.entity.LikeStatus
import com.goldpet.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface LikeRepository : JpaRepository<Like, Long> {
    fun findByFromUserAndToUser(fromUser: User, toUser: User): Optional<Like>
    
    // Find all active likes I sent
    fun findByFromUserIdAndStatus(fromUserId: Long, status: LikeStatus): List<Like>
    
    // Find all active likes I received
    fun findByToUserIdAndStatus(toUserId: Long, status: LikeStatus): List<Like>
    
    // Check if user liked me back (for mutual check)
    fun existsByFromUserIdAndToUserIdAndStatus(fromUserId: Long, toUserId: Long, status: LikeStatus): Boolean
    
    // Find mutual likes (서로 좋아해) - use native query for reliable status comparison
    @Query(value = """
        SELECT l.* FROM likes l 
        WHERE l.from_user_id = :userId 
        AND l.status = 'ACTIVE'
        AND EXISTS (
            SELECT 1 FROM likes l2 
            WHERE l2.from_user_id = l.to_user_id 
            AND l2.to_user_id = :userId 
            AND l2.status = 'ACTIVE'
        )
        ORDER BY l.created_at DESC
    """, nativeQuery = true)
    fun findMutualLikes(@Param("userId") userId: Long): List<Like>

    // Find likes received ONLY (excluding mutual) - so they disappear from "Received" when liked back
    @Query(value = """
        SELECT l.* FROM likes l 
        WHERE l.to_user_id = :userId 
        AND l.status = 'ACTIVE'
        AND NOT EXISTS (
            SELECT 1 FROM likes l2 
            WHERE l2.from_user_id = :userId 
            AND l2.to_user_id = l.from_user_id 
            AND l2.status = 'ACTIVE'
        )
        ORDER BY l.created_at DESC
    """, nativeQuery = true)
    fun findReceivedOnlyLikes(@Param("userId") userId: Long): List<Like>

    // Find likes sent ONLY (excluding mutual) - so they disappear from "My Likes" when mutual
    @Query(value = """
        SELECT l.* FROM likes l
        WHERE l.from_user_id = :userId
        AND l.status = 'ACTIVE'
        AND NOT EXISTS (
            SELECT 1 FROM likes l2
            WHERE l2.from_user_id = l.to_user_id
            AND l2.to_user_id = :userId
            AND l2.status = 'ACTIVE'
        )
        ORDER BY l.created_at DESC
    """, nativeQuery = true)
    fun findSentOnlyLikes(@Param("userId") userId: Long): List<Like>

    fun countByToUserIdAndStatus(toUserId: Long, status: LikeStatus): Long
}
