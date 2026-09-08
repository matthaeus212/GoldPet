package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.dto.BackupJobResponse
import com.goldpet.domain.admin.entity.BackupJob
import com.goldpet.domain.admin.entity.BackupJobStatus
import com.goldpet.domain.admin.entity.BackupTriggerType
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.admin.repository.BackupJobRepository
import com.goldpet.domain.common.exception.ConflictException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class BackupJobService(
    private val backupJobRepository: BackupJobRepository,
    private val adminUserRepository: AdminUserRepository
) {
    companion object {
        private val ACTIVE_STATUSES = listOf(BackupJobStatus.PENDING, BackupJobStatus.RUNNING)
        private const val DEFAULT_LIMIT = 20
    }

    @Transactional
    fun trigger(currentAdminId: Long): BackupJobResponse {
        if (backupJobRepository.existsByStatusIn(ACTIVE_STATUSES)) {
            throw ConflictException("이미 진행 중인 백업이 있습니다", "BACKUP_IN_PROGRESS")
        }
        val job = BackupJob(
            status = BackupJobStatus.PENDING,
            triggeredByAdminId = currentAdminId,
            triggerType = BackupTriggerType.MANUAL,
            createdAt = LocalDateTime.now()
        )
        return backupJobRepository.save(job).toResponse()
    }

    @Transactional(readOnly = true)
    fun listJobs(limit: Int = DEFAULT_LIMIT): List<BackupJobResponse> {
        val capped = limit.coerceIn(1, 100)
        val jobs = backupJobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, capped))
        val adminIds = jobs.mapNotNull { it.triggeredByAdminId }.toSet()
        val emailById = if (adminIds.isEmpty()) {
            emptyMap()
        } else {
            adminUserRepository.findAllById(adminIds).associate { it.id to it.email }
        }
        return jobs.map { it.toResponse(emailById[it.triggeredByAdminId]) }
    }

    private fun BackupJob.toResponse(email: String? = null): BackupJobResponse = BackupJobResponse(
        id = id,
        status = status,
        triggeredByAdminId = triggeredByAdminId,
        triggeredByAdminEmail = email,
        triggerType = triggerType,
        startedAt = startedAt,
        finishedAt = finishedAt,
        filePath = filePath,
        fileSizeBytes = fileSizeBytes,
        errorMessage = errorMessage,
        createdAt = createdAt
    )
}
