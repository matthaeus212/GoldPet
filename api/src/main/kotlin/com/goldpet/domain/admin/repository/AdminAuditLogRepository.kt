package com.goldpet.domain.admin.repository

import com.goldpet.domain.admin.entity.AdminAuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, Long> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<AdminAuditLog>
    fun findByAdminUserIdOrderByCreatedAtDesc(adminUserId: Long, pageable: Pageable): Page<AdminAuditLog>
    fun findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType: String, targetId: Long, pageable: Pageable): Page<AdminAuditLog>
}
