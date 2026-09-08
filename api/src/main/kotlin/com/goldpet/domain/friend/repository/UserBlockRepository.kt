package com.goldpet.domain.friend.repository

import com.goldpet.domain.friend.entity.UserBlock
import com.goldpet.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface UserBlockRepository : JpaRepository<UserBlock, Long> {
    fun findByBlockerAndBlocked(blocker: User, blocked: User): Optional<UserBlock>
    fun findAllByBlocker(blocker: User): List<UserBlock>
    fun existsByBlockerAndBlocked(blocker: User, blocked: User): Boolean
    fun existsByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Boolean

    @Query("""
        SELECT ub.blocked_id FROM user_blocks ub WHERE ub.blocker_id = :userId
        UNION
        SELECT ub.blocker_id FROM user_blocks ub WHERE ub.blocked_id = :userId
    """, nativeQuery = true)
    fun findBlockedUserIds(@Param("userId") userId: Long): List<Long>
}
