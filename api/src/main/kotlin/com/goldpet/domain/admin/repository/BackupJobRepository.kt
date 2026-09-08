package com.goldpet.domain.admin.repository

import com.goldpet.domain.admin.entity.BackupJob
import com.goldpet.domain.admin.entity.BackupJobStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface BackupJobRepository : JpaRepository<BackupJob, Long> {
    fun findFirstByStatusInOrderByCreatedAtDesc(statuses: Collection<BackupJobStatus>): BackupJob?
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<BackupJob>
    fun existsByStatusIn(statuses: Collection<BackupJobStatus>): Boolean
}
