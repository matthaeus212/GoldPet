package com.goldpet.domain.chat.repository

import com.goldpet.domain.chat.entity.ChatRoomParticipant
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ChatRoomParticipantRepository : JpaRepository<ChatRoomParticipant, Long> {
    fun findByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Optional<ChatRoomParticipant>
    /**
     * 라운드2 리뷰 MEDIUM(leftAt) — 나간 참여자(leftAt≠null)를 접근 인가 검증에서 배제.
     * getChatHistory/saveChatMessage/markAsRead/SUBSCRIBE/typing 등 "지금 이 방에 접근 권한이
     * 있는가"를 묻는 경로 전용. leaveRoom 자체·재입장(rejoin) 재활성화·addParticipant 중복검사처럼
     * leftAt 여부와 무관하게 row 자체가 필요한 경로는 기존 [findByChatRoomIdAndUserId] 를 유지한다.
     */
    fun findByChatRoomIdAndUserIdAndLeftAtIsNull(chatRoomId: Long, userId: Long): Optional<ChatRoomParticipant>
    fun findAllByUserId(userId: Long): List<ChatRoomParticipant>
    fun findAllByUserIdAndLeftAtIsNull(userId: Long): List<ChatRoomParticipant>
    fun findAllByChatRoomId(chatRoomId: Long): List<ChatRoomParticipant>
}
