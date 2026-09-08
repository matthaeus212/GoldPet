package com.goldpet.domain.gold.repository

import com.goldpet.domain.gold.entity.GoldIdempotencyKey
import com.goldpet.domain.gold.entity.IdempotencyStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface GoldIdempotencyKeyRepository : JpaRepository<GoldIdempotencyKey, String> {

    /**
     * Atomic INSERT ... ON CONFLICT DO NOTHING (Postgres).
     * Returns 1 if newly inserted (caller proceeds with transaction),
     * 0 if duplicate key (caller must SELECT existing row by [findById] and
     * branch on [GoldIdempotencyKey.status] + verify [GoldIdempotencyKey.requestHash]).
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO gold_idempotency_keys
                (idempotency_key, user_id, request_hash, status, created_at, expires_at)
            VALUES
                (:key, :userId, :requestHash, 'PROCESSING', now(), now() + INTERVAL '7 days')
            ON CONFLICT (idempotency_key) DO NOTHING
        """,
        nativeQuery = true
    )
    fun tryInsert(
        @Param("key") key: String,
        @Param("userId") userId: Long,
        @Param("requestHash") requestHash: String,
    ): Int

    @Modifying
    @Query(
        """
        UPDATE GoldIdempotencyKey k
           SET k.status        = :status,
               k.transactionId = :transactionId,
               k.responseBody  = :responseBody,
               k.httpStatus    = :httpStatus,
               k.completedAt   = :now
         WHERE k.idempotencyKey = :key
        """
    )
    fun markCompleted(
        @Param("key") key: String,
        @Param("status") status: IdempotencyStatus,
        @Param("transactionId") transactionId: Long?,
        @Param("responseBody") responseBody: String?,
        @Param("httpStatus") httpStatus: Short,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying
    @Query("DELETE FROM GoldIdempotencyKey k WHERE k.expiresAt < :now")
    fun deleteExpired(@Param("now") now: LocalDateTime): Int
}
