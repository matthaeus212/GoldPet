package com.goldpet.domain.auth.repository

import com.goldpet.domain.auth.entity.RefreshTokenRevokeReason
import com.goldpet.domain.auth.entity.UserRefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface UserRefreshTokenRepository : JpaRepository<UserRefreshToken, Long> {

    fun findByTokenHash(tokenHash: String): Optional<UserRefreshToken>

    /**
     * 주어진 parent 로부터 회전 발급된 모든 자식 token.
     * 2026-07 dual-client desync 관용 정책(chain-walk)의 핵심 조회 — branch 발급 허용으로
     * 한 부모가 자식을 2개 이상 가질 수 있으므로(같은 parent 로 여러 번 stale-generation 회전)
     * 단건(Optional)이 아닌 List 로 반환한다. 단건 반환 시 자식이 2개 이상이면
     * `IncorrectResultSizeDataAccessException` 이 터져 rotate() 가 500 으로 깨진다 — 절대 Optional/단건으로
     * 되돌리지 말 것. 호출 측(`RefreshTokenRotationService.rotate` case c)이 살아있는 자식 우선,
     * 없으면 ROTATED 자식 중 issuedAt 최신을 골라 walk 를 이어간다.
     */
    fun findByParentTokenHash(parentTokenHash: String): List<UserRefreshToken>

    /**
     * Active (not revoked, not expired) tokens for a (user, device).
     * Used for diagnostics / logout-all-on-device.
     */
    @Query(
        """
        SELECT t FROM UserRefreshToken t
         WHERE t.userId = :userId
           AND t.deviceId = :deviceId
           AND t.revokedAt IS NULL
           AND t.expiresAt > :now
        """
    )
    fun findActiveByUserAndDevice(
        @Param("userId") userId: Long,
        @Param("deviceId") deviceId: String,
        @Param("now") now: LocalDateTime,
    ): List<UserRefreshToken>

    /**
     * Revoke ALL active tokens for (user_id, device_id). 사용처:
     *  - reuse detection (이미 revoked된 token 재사용 시도 → 해당 device chain 전체 revoke)
     *  - 사용자 명시 로그아웃 (LOGOUT)
     *  - Admin 강제 로그아웃 (ADMIN_FORCE)
     */
    @Modifying
    @Query(
        """
        UPDATE UserRefreshToken t
           SET t.revokedAt = :now, t.revokeReason = :reason
         WHERE t.userId = :userId
           AND t.deviceId = :deviceId
           AND t.revokedAt IS NULL
        """
    )
    fun revokeAllForDevice(
        @Param("userId") userId: Long,
        @Param("deviceId") deviceId: String,
        @Param("now") now: LocalDateTime,
        @Param("reason") reason: RefreshTokenRevokeReason,
    ): Int

    /** 단일 token revoke (회전 시 parent revoke 용). */
    @Modifying
    @Query(
        """
        UPDATE UserRefreshToken t
           SET t.revokedAt = :now, t.revokeReason = :reason
         WHERE t.tokenHash = :tokenHash
           AND t.revokedAt IS NULL
        """
    )
    fun revokeByTokenHash(
        @Param("tokenHash") tokenHash: String,
        @Param("now") now: LocalDateTime,
        @Param("reason") reason: RefreshTokenRevokeReason,
    ): Int

    /** 만료된 token 정리 (cron). */
    @Modifying
    @Query("DELETE FROM UserRefreshToken t WHERE t.expiresAt < :now")
    fun deleteExpired(@Param("now") now: LocalDateTime): Int
}
