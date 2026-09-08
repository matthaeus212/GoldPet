package com.goldpet.domain.admin.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

enum class BackupJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}

enum class BackupTriggerType {
    AUTO_CRON,
    MANUAL
}

@Entity
@Table(name = "backup_jobs")
class BackupJob(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BackupJobStatus,

    @Column(name = "triggered_by_admin_id")
    var triggeredByAdminId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    var triggerType: BackupTriggerType,

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null,

    @Column(name = "file_path", columnDefinition = "TEXT")
    var filePath: String? = null,

    @Column(name = "file_size_bytes")
    var fileSizeBytes: Long? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
