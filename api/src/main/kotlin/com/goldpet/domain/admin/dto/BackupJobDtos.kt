package com.goldpet.domain.admin.dto

import com.goldpet.domain.admin.entity.BackupJobStatus
import com.goldpet.domain.admin.entity.BackupTriggerType
import java.time.LocalDateTime

data class BackupJobResponse(
    val id: Long,
    val status: BackupJobStatus,
    val triggeredByAdminId: Long?,
    val triggeredByAdminEmail: String?,
    val triggerType: BackupTriggerType,
    val startedAt: LocalDateTime?,
    val finishedAt: LocalDateTime?,
    val filePath: String?,
    val fileSizeBytes: Long?,
    val errorMessage: String?,
    val createdAt: LocalDateTime
)
