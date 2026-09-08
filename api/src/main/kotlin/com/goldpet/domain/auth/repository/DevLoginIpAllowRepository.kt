// dev-login 허용 IP 엔트리 저장소
package com.goldpet.domain.auth.repository

import com.goldpet.domain.auth.entity.DevLoginIpAllow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

interface DevLoginIpAllowRepository : JpaRepository<DevLoginIpAllow, Long> {

    fun findByIpPattern(ipPattern: String): Optional<DevLoginIpAllow>

    /**
     * 현재 유효한 엔트리(활성 + 미만료).
     *
     * 캐시하지 않는다 — 어드민에서 IP 를 고치면 **즉시** 반영돼야 한다.
     * (SystemSetting 은 Redis 24h TTL 캐시라, 잠겼을 때 직접 SQL 로 고쳐도 최대 24시간 안 먹는다.
     *  잠김 복구가 필요한 순간에 안 듣는 통제는 없느니만 못하다.)
     */
    @Query(
        """
        SELECT e FROM DevLoginIpAllow e
         WHERE e.enabled = true
           AND (e.expiresAt IS NULL OR e.expiresAt > :now)
        """
    )
    fun findActive(@Param("now") now: LocalDateTime): List<DevLoginIpAllow>
}
