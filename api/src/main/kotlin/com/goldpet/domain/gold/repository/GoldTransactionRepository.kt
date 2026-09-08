package com.goldpet.domain.gold.repository

import com.goldpet.domain.gold.entity.GoldTransaction
import com.goldpet.domain.gold.entity.TransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface GoldTransactionRepository : JpaRepository<GoldTransaction, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<GoldTransaction>
    
    @Query("SELECT SUM(t.amount) FROM GoldTransaction t WHERE t.user.id = :userId AND t.type = :type AND t.createdAt >= :since")
    fun sumAmountByUserAndTypeSince(
        @Param("userId") userId: Long,
        @Param("type") type: TransactionType,
        @Param("since") since: LocalDateTime
    ): Int?

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM GoldTransaction t WHERE t.type = :type")
    fun sumAmountByType(@Param("type") type: TransactionType): Long

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM GoldTransaction t WHERE t.type = :type AND t.createdAt >= :since")
    fun sumAmountByTypeSince(@Param("type") type: TransactionType, @Param("since") since: LocalDateTime): Long

    @Query(value = "SELECT DATE(created_at) as date, COALESCE(SUM(amount), 0) as total FROM gold_transactions WHERE type = 'CHARGE' AND created_at >= :since GROUP BY DATE(created_at) ORDER BY date", nativeQuery = true)
    fun sumByDateGrouped(@Param("since") since: LocalDateTime): List<Array<Any>>

    @Query("""
        SELECT t FROM GoldTransaction t JOIN FETCH t.user
        WHERE (:type IS NULL OR t.type = :type)
        ORDER BY t.createdAt DESC
    """,
    countQuery = "SELECT COUNT(t) FROM GoldTransaction t WHERE (:type IS NULL OR t.type = :type)")
    fun findAllForAdmin(@Param("type") type: TransactionType?, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<GoldTransaction>

    fun existsByOriginalTransactionId(originalTransactionId: Long): Boolean

    fun existsByUserIdAndReferenceType(userId: Long, referenceType: String): Boolean

    fun findAllByUserIdAndTypeOrderByCreatedAtDesc(userId: Long, type: TransactionType, pageable: Pageable): Page<GoldTransaction>
}
