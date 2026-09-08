package com.goldpet.domain.report.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

enum class ReportType {
    POST, COMMENT, USER, CHAT, COURSE, WALK_SPOT
}

enum class ReportStatus {
    PENDING, RESOLVED, DISMISSED
}

@Entity
@Table(name = "reports")
class Report(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: ReportType,

    @Column(name = "target_id", nullable = false)
    val targetId: Long,

    @Column(name = "target_preview")
    val targetPreview: String? = null,

    @Column(nullable = false, length = 500)
    val reason: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    val reporter: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ReportStatus = ReportStatus.PENDING,

    @Column(name = "resolved_at")
    var resolvedAt: LocalDateTime? = null,

    @Column(name = "resolved_by")
    var resolvedByAdminId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type")
    var actionType: ReportActionType? = null,

    @Column(name = "admin_note")
    var adminNote: String? = null

) : BaseTimeEntity()
