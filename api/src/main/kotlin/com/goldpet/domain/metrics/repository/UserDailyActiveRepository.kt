package com.goldpet.domain.metrics.repository

import com.goldpet.domain.metrics.entity.UserDailyActive
import com.goldpet.domain.metrics.entity.UserDailyActiveId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Repository
interface UserDailyActiveRepository : JpaRepository<UserDailyActive, UserDailyActiveId> {

    /**
     * Idempotent upsert: records that [userId] was active on [activeDate].
     * Returns 1 when a row was inserted, 0 when it already existed (ON CONFLICT).
     * Self-transactional so it is safe to call standalone (it also joins an
     * existing transaction when called from [com.goldpet.domain.metrics.service.DailyActiveService]).
     */
    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO user_daily_active (user_id, active_date)
            VALUES (:userId, :activeDate)
            ON CONFLICT (user_id, active_date) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("userId") userId: Long,
        @Param("activeDate") activeDate: LocalDate,
    ): Int

    fun countByUserId(userId: Long): Long

    /** GDPR purge: removes the withdrawn user's daily-active rows. */
    @Modifying
    @Transactional
    fun deleteByUserId(userId: Long): Long

    /** TTL retention: drops daily-active rows older than [cutoff]. */
    @Modifying
    @Transactional
    @Query(
        value = "DELETE FROM user_daily_active WHERE active_date < :cutoff",
        nativeQuery = true,
    )
    fun deleteByActiveDateBefore(@Param("cutoff") cutoff: LocalDate): Int
}
