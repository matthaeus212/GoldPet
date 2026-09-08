package com.goldpet.domain.chat.repository

import com.goldpet.domain.chat.entity.ChatRequest
import com.goldpet.domain.chat.entity.ChatRequestStatus
import com.goldpet.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRequestRepository : JpaRepository<ChatRequest, Long> {
    fun findByTargetUserAndStatus(targetUser: User, status: ChatRequestStatus): List<ChatRequest>
    fun findByRequesterAndStatus(requester: User, status: ChatRequestStatus): List<ChatRequest>
    fun findByTargetUser(targetUser: User): List<ChatRequest>
}
