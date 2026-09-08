package com.goldpet.domain.aiprofile.repository

import com.goldpet.domain.aiprofile.entity.AIProfileRequest
import com.goldpet.domain.aiprofile.entity.AIRequestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface AIProfileRequestRepository : JpaRepository<AIProfileRequest, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<AIProfileRequest>
    fun findAllByPetIdOrderByCreatedAtDesc(petId: Long, pageable: Pageable): Page<AIProfileRequest>
    fun findAllByStatus(status: AIRequestStatus): List<AIProfileRequest>
    fun countByUserIdAndStatus(userId: Long, status: AIRequestStatus): Long

    @Query("SELECT COUNT(r) FROM AIProfileRequest r WHERE r.user.id = :userId AND r.createdAt >= :startOfDay AND r.createdAt < :startOfNextDay")
    fun countByUserIdAndCreatedAtBetween(
        @Param("userId") userId: Long,
        @Param("startOfDay") startOfDay: LocalDateTime,
        @Param("startOfNextDay") startOfNextDay: LocalDateTime
    ): Long

    @Query("""
        SELECT r FROM AIProfileRequest r JOIN FETCH r.user JOIN FETCH r.pet
        WHERE (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC
    """,
    countQuery = """
        SELECT COUNT(r) FROM AIProfileRequest r
        WHERE (:status IS NULL OR r.status = :status)
    """)
    fun findAllForAdmin(
        @Param("status") status: AIRequestStatus?,
        pageable: Pageable
    ): Page<AIProfileRequest>
}
