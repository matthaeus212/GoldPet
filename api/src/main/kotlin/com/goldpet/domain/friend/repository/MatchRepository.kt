package com.goldpet.domain.friend.repository

import com.goldpet.domain.friend.entity.Match
import com.goldpet.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface MatchRepository : JpaRepository<Match, Long> {
    fun findByUser1AndUser2(user1: User, user2: User): Optional<Match>
    
    @Query("SELECT m FROM Match m WHERE m.user1.id = :userId OR m.user2.id = :userId ORDER BY m.createdAt DESC")
    fun findAllByUserId(@Param("userId") userId: Long): List<Match>
    
    @Query("SELECT m FROM Match m WHERE (m.user1.id = :userId1 AND m.user2.id = :userId2) OR (m.user1.id = :userId2 AND m.user2.id = :userId1)")
    fun findByUserIds(@Param("userId1") userId1: Long, @Param("userId2") userId2: Long): Optional<Match>

    @Query("SELECT COUNT(m) FROM Match m WHERE m.user1.id = :userId OR m.user2.id = :userId")
    fun countByUserId(@Param("userId") userId: Long): Long
}
