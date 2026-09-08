package com.goldpet.domain.auth.repository

import com.goldpet.domain.auth.entity.OAuthNonce
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface OAuthNonceRepository : JpaRepository<OAuthNonce, UUID> {

    /**
     * Atomic consume — 1회용 검증. used_at IS NULL AND expires_at > now() 조건 모두 만족 시 used_at=now() 로 update.
     * 반환:
     *  - 1: 정상 consume — caller proceeds.
     *  - 0: 이미 used, 만료, 또는 존재하지 않음 — caller MUST reject (replay 시도).
     */
    @Modifying
    @Query(
        value = """
            UPDATE oauth_nonces
               SET used_at = now()
             WHERE nonce_uuid = :nonceUuid
               AND used_at IS NULL
               AND expires_at > now()
        """,
        nativeQuery = true,
    )
    fun tryConsume(@Param("nonceUuid") nonceUuid: UUID): Int

    /** Cleanup job (daily cron). */
    @Modifying
    @Query("DELETE FROM OAuthNonce n WHERE n.expiresAt < :now")
    fun deleteExpired(@Param("now") now: LocalDateTime): Int
}
