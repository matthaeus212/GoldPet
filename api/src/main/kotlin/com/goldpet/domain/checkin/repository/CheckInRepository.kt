package com.goldpet.domain.checkin.repository

import com.goldpet.domain.checkin.entity.CheckIn
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CheckInRepository : JpaRepository<CheckIn, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<CheckIn>
    fun findAllByPlaceIdOrderByCreatedAtDesc(placeId: Long, pageable: Pageable): Page<CheckIn>
    fun countByUserId(userId: Long): Long
    fun countByUserIdAndCreatedAtAfter(userId: Long, after: LocalDateTime): Long
    fun existsByUserIdAndPlaceIdAndCreatedAtAfter(userId: Long, placeId: Long, after: LocalDateTime): Boolean

    @Query(value = """
        SELECT p.id, p.name, COUNT(c.id) as visit_count
        FROM check_ins c JOIN places p ON c.place_id = p.id
        GROUP BY p.id, p.name
        ORDER BY visit_count DESC
        LIMIT :limit
    """, nativeQuery = true)
    fun findTopPlaces(@Param("limit") limit: Int): List<Array<Any>>
}
