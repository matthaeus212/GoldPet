package com.goldpet.domain.user.repository

import com.goldpet.domain.user.entity.DeletedUser
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface DeletedUserRepository : JpaRepository<DeletedUser, Long> {
    fun findByUserId(userId: Long): DeletedUser?
    fun findByDataDeletionScheduledAtBefore(dateTime: LocalDateTime): List<DeletedUser>
}
