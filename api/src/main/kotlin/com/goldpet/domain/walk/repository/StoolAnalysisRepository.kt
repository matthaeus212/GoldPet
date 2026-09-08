package com.goldpet.domain.walk.repository

import com.goldpet.domain.walk.entity.AnalysisStatus
import com.goldpet.domain.walk.entity.StoolAnalysis
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface DailyCountRow {
    fun getDate(): String
    fun getCount(): Long
}

interface StoolAnalysisRepository : JpaRepository<StoolAnalysis, Long> {
    fun findByPetIdOrderByCreatedAtDesc(petId: Long, pageable: Pageable): Page<StoolAnalysis>
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<StoolAnalysis>
    fun findByWalkSpotId(walkSpotId: Long): StoolAnalysis?
    fun countByPetId(petId: Long): Long
    fun findByPetIdAndCreatedAtBetween(petId: Long, start: LocalDateTime, end: LocalDateTime): List<StoolAnalysis>
    fun countByUserIdAndCreatedAtAfter(userId: Long, after: LocalDateTime): Long
    fun findByStatusAndCreatedAtBefore(status: AnalysisStatus, before: LocalDateTime): List<StoolAnalysis>
    fun countByStatus(status: AnalysisStatus): Long

    @Query("SELECT AVG(s.overallScore) FROM StoolAnalysis s WHERE s.status = com.goldpet.domain.walk.entity.AnalysisStatus.COMPLETED AND s.overallScore IS NOT NULL")
    fun avgOverallScore(): Double?

    @Query(
        value = "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS date, COUNT(*) AS count FROM stool_analyses WHERE created_at >= :since GROUP BY TO_CHAR(created_at, 'YYYY-MM-DD') ORDER BY 1 DESC",
        nativeQuery = true
    )
    fun dailyCountsSince(@Param("since") since: LocalDateTime): List<DailyCountRow>

    @Query("SELECT s FROM StoolAnalysis s WHERE (:status IS NULL OR s.status = :status) ORDER BY s.createdAt DESC")
    fun findAllForAdmin(@Param("status") status: AnalysisStatus?, pageable: Pageable): Page<StoolAnalysis>
}
