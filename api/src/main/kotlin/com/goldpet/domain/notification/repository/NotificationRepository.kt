package com.goldpet.domain.notification.repository

import com.goldpet.domain.notification.entity.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<Notification>
    
    fun findAllByUserIdAndTypeInOrderByCreatedAtDesc(
        userId: Long, 
        types: List<com.goldpet.domain.notification.entity.NotificationType>, 
        pageable: Pageable
    ): Page<Notification>

    fun countByUserIdAndIsReadFalse(userId: Long): Long
    
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    fun markAllAsRead(@Param("userId") userId: Long): Int
}
