package com.goldpet.domain.admin.repository

import com.goldpet.domain.admin.entity.PushDeliveryLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PushDeliveryLogRepository : JpaRepository<PushDeliveryLog, Long> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<PushDeliveryLog>
}
