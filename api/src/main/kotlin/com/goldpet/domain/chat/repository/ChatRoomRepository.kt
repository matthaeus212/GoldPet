package com.goldpet.domain.chat.repository

import com.goldpet.domain.chat.entity.ChatRoom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface ChatRoomRepository : JpaRepository<ChatRoom, Long> {
    fun findByMatchId(matchId: Long): Optional<ChatRoom>

    @Query("""
        SELECT cr FROM ChatRoom cr
        WHERE cr.roomType = com.goldpet.domain.chat.entity.ChatRoomType.DIRECT
        AND EXISTS (SELECT 1 FROM ChatRoomParticipant p1 WHERE p1.chatRoom = cr AND p1.user.id = :userId1)
        AND EXISTS (SELECT 1 FROM ChatRoomParticipant p2 WHERE p2.chatRoom = cr AND p2.user.id = :userId2)
    """)
    fun findDirectRoomBetweenUsers(@Param("userId1") userId1: Long, @Param("userId2") userId2: Long): Optional<ChatRoom>
}
