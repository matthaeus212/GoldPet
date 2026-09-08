package com.goldpet.domain.user.repository

import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserStatus
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    fun findByOauthProviderAndOauthId(oauthProvider: String, oauthId: String): Optional<User>
    fun findByUsername(username: String): Optional<User>
    @Query("SELECT u FROM User u WHERE u.emailHash = :emailHash")
    fun findByEmailHash(@Param("emailHash") emailHash: String): Optional<User>

    @Deprecated("Use findByEmailHash instead") fun findByEmail(email: String): Optional<User>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Optional<User>

    fun findByNickname(nickname: String): Optional<User>

    // Admin methods
    fun findByNicknameContainingIgnoreCase(nickname: String, pageable: Pageable): Page<User>
    // 회원 목록: 상태 필터가 없으면 탈퇴(WITHDRAWN) 회원은 기본 제외, 명시 선택 시에만 조회
    fun findByStatus(status: UserStatus, pageable: Pageable): Page<User>
    fun findByStatusNot(status: UserStatus, pageable: Pageable): Page<User>
    fun findByNicknameContainingIgnoreCaseAndStatus(nickname: String, status: UserStatus, pageable: Pageable): Page<User>
    fun findByNicknameContainingIgnoreCaseAndStatusNot(nickname: String, status: UserStatus, pageable: Pageable): Page<User>
    fun countByCreatedAtAfter(date: LocalDateTime): Long
    // 회원가입 축하 골드 소급 지급 대상 (가입 완료 + 활성 회원)
    fun findAllBySignupCompletedAtIsNotNullAndStatus(status: UserStatus): List<User>

    @Query(
            value =
                    "SELECT * FROM users u WHERE u.is_active = true AND u.is_profile_public = true AND u.is_location_sharing_enabled = true AND ST_Intersects(u.main_location_geom, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))",
            nativeQuery = true
    )
    fun findUsersWithinBoundingBox(
            @Param("minLat") minLat: Double,
            @Param("minLon") minLon: Double,
            @Param("maxLat") maxLat: Double,
            @Param("maxLon") maxLon: Double
    ): List<User>

    @Query(
            value =
                    "SELECT * FROM users u WHERE u.main_location_geom IS NOT NULL AND u.is_active = true AND u.is_profile_public = true AND u.is_location_sharing_enabled = true AND ST_DistanceSphere(u.main_location_geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)) <= :radius",
            nativeQuery = true
    )
    fun findUsersWithinRadius(
            @Param("lat") lat: Double,
            @Param("lon") lon: Double,
            @Param("radius") radius: Double
    ): List<User>

    @Query("SELECT COALESCE(SUM(u.goldBalance), 0) FROM User u") fun sumGoldBalance(): Long

    @Query(
            value =
                    "SELECT DATE(created_at) as date, COUNT(*) as cnt FROM users WHERE created_at >= :since GROUP BY DATE(created_at) ORDER BY date",
            nativeQuery = true
    )
    fun countByDateGrouped(@Param("since") since: LocalDateTime): List<Array<Any>>

    @Modifying
    @Query(
            "UPDATE User u SET u.fcmToken = null WHERE u.fcmToken IS NOT NULL AND u.updatedAt < :cutoffDate"
    )
    fun clearFcmTokensForInactiveUsers(@Param("cutoffDate") cutoffDate: LocalDateTime): Int

    /**
     * 휴면 전환 후보: ACTIVE 이면서 [cutoff] 이전에 마지막으로 접속한 사용자.
     * lastLoginAt 이 NULL 이면 판정 기준이 없으므로 제외한다(STYLE-001).
     */
    @Query(
        """
        SELECT u FROM User u
         WHERE u.status = com.goldpet.domain.user.entity.UserStatus.ACTIVE
           AND u.lastLoginAt IS NOT NULL
           AND u.lastLoginAt < :cutoff
         ORDER BY u.lastLoginAt ASC
        """
    )
    fun findDormancyCandidates(
        @Param("cutoff") cutoff: java.time.LocalDateTime,
        pageable: org.springframework.data.domain.Pageable
    ): List<User>
}
