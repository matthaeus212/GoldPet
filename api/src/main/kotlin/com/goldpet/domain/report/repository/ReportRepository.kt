package com.goldpet.domain.report.repository

import com.goldpet.domain.report.entity.Report
import com.goldpet.domain.report.entity.ReportStatus
import com.goldpet.domain.report.entity.ReportType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ReportRepository : JpaRepository<Report, Long> {
    fun findByStatus(status: ReportStatus, pageable: Pageable): Page<Report>
    fun countByStatus(status: ReportStatus): Long
    fun findAllByReporterId(reporterId: Long): List<Report>
    fun existsByReporterIdAndTypeAndTargetIdAndStatus(
        reporterId: Long,
        type: ReportType,
        targetId: Long,
        status: ReportStatus
    ): Boolean

    fun countByTypeAndTargetIdAndStatusNot(type: ReportType, targetId: Long, status: ReportStatus): Long
}
