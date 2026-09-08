package com.goldpet.domain.admin.repository

import com.goldpet.domain.admin.entity.NotificationTemplate
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationTemplateRepository : JpaRepository<NotificationTemplate, Long>
