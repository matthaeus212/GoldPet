package com.goldpet.domain.friend.repository

import com.goldpet.domain.user.entity.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FriendRepository : JpaRepository<User, Long> {
    // Find users within distance, ordered by registration (created_at DESC)
    @Query(value = """
        SELECT u.*
        FROM users u
        WHERE u.id != :myUserId
        AND u.is_active = true
        AND u.is_profile_public = true
        AND u.is_location_sharing_enabled = true
        AND EXISTS (SELECT 1 FROM pets p WHERE p.owner_user_id = u.id)
        AND ST_DWithin(
            CAST(u.main_location_geom AS geography),
            CAST(ST_SetSRID(ST_MakePoint(:myLng, :myLat), 4326) AS geography),
            :radiusMeters
        )
        AND NOT EXISTS (
            SELECT 1 FROM likes l
            WHERE l.from_user_id = :myUserId
            AND l.to_user_id = u.id
            AND l.status = 'ACTIVE'
        )
        AND NOT EXISTS (
            SELECT 1 FROM user_blocks ub
            WHERE (ub.blocker_id = :myUserId AND ub.blocked_id = u.id)
               OR (ub.blocker_id = u.id AND ub.blocked_id = :myUserId)
        )
        ORDER BY u.created_at DESC
    """, countQuery = """
        SELECT count(DISTINCT u.id)
        FROM users u
        INNER JOIN pets p ON u.id = p.owner_user_id
        WHERE u.id != :myUserId
        AND u.is_active = true
        AND u.is_profile_public = true
        AND u.is_location_sharing_enabled = true
        AND ST_DWithin(
            CAST(u.main_location_geom AS geography),
            CAST(ST_SetSRID(ST_MakePoint(:myLng, :myLat), 4326) AS geography),
            :radiusMeters
        )
        AND NOT EXISTS (
            SELECT 1 FROM likes l
            WHERE l.from_user_id = :myUserId
            AND l.to_user_id = u.id
            AND l.status = 'ACTIVE'
        )
        AND NOT EXISTS (
            SELECT 1 FROM user_blocks ub
            WHERE (ub.blocker_id = :myUserId AND ub.blocked_id = u.id)
               OR (ub.blocker_id = u.id AND ub.blocked_id = :myUserId)
        )
    """, nativeQuery = true)
    fun findFriendsNearbyByRegistered(
        @Param("myUserId") myUserId: Long,
        @Param("myLat") myLat: Double,
        @Param("myLng") myLng: Double,
        @Param("radiusMeters") radiusMeters: Double,
        pageable: Pageable
    ): Page<User>

    // Find users within distance, ordered by likes count (popularity)
    @Query(value = """
        SELECT u.*, COUNT(l2.id) AS like_count
        FROM users u
        INNER JOIN pets p ON u.id = p.owner_user_id
        LEFT JOIN likes l2 ON u.id = l2.to_user_id AND l2.status = 'ACTIVE'
        WHERE u.id != :myUserId
        AND u.is_active = true
        AND u.is_profile_public = true
        AND u.is_location_sharing_enabled = true
        AND ST_DWithin(
            CAST(u.main_location_geom AS geography),
            CAST(ST_SetSRID(ST_MakePoint(:myLng, :myLat), 4326) AS geography),
            :radiusMeters
        )
        AND NOT EXISTS (
            SELECT 1 FROM likes l
            WHERE l.from_user_id = :myUserId
            AND l.to_user_id = u.id
            AND l.status = 'ACTIVE'
        )
        AND NOT EXISTS (
            SELECT 1 FROM user_blocks ub
            WHERE (ub.blocker_id = :myUserId AND ub.blocked_id = u.id)
               OR (ub.blocker_id = u.id AND ub.blocked_id = :myUserId)
        )
        GROUP BY u.id
        ORDER BY like_count DESC, u.created_at DESC
    """, countQuery = """
        SELECT count(DISTINCT u.id)
        FROM users u
        INNER JOIN pets p ON u.id = p.owner_user_id
        WHERE u.id != :myUserId
        AND u.is_active = true
        AND u.is_profile_public = true
        AND u.is_location_sharing_enabled = true
        AND ST_DWithin(
            CAST(u.main_location_geom AS geography),
            CAST(ST_SetSRID(ST_MakePoint(:myLng, :myLat), 4326) AS geography),
            :radiusMeters
        )
        AND NOT EXISTS (
            SELECT 1 FROM likes l
            WHERE l.from_user_id = :myUserId
            AND l.to_user_id = u.id
            AND l.status = 'ACTIVE'
        )
        AND NOT EXISTS (
            SELECT 1 FROM user_blocks ub
            WHERE (ub.blocker_id = :myUserId AND ub.blocked_id = u.id)
               OR (ub.blocker_id = u.id AND ub.blocked_id = :myUserId)
        )
    """, nativeQuery = true)
    fun findFriendsNearbyByPopular(
        @Param("myUserId") myUserId: Long,
        @Param("myLat") myLat: Double,
        @Param("myLng") myLng: Double,
        @Param("radiusMeters") radiusMeters: Double,
        pageable: Pageable
    ): Page<User>

    // Find users within distance, ordered by distance (closest first)
    @Query(value = """
        SELECT u.*, ST_Distance(CAST(u.main_location_geom AS geography), CAST(ST_SetSRID(ST_MakePoint(:myLng, :myLat), 4326) AS geography)) AS dist
        FROM users u
        INNER JOIN pets p ON u.id = p.owner_user_id
        WHERE u.id != :myUserId
        AND u.is_active = true
        AND u.is_profile_public = true
        AND u.is_location_sharing_enabled = true
        AND ST_DWithin(
            CAST(u.main_location_geom AS geography),
            CAST(ST_SetSRID(ST_MakePoint(:myLng, :myLat), 4326) AS geography),
            :radiusMeters
        )
        AND NOT EXISTS (
            SELECT 1 FROM likes l
            WHERE l.from_user_id = :myUserId
            AND l.to_user_id = u.id
            AND l.status = 'ACTIVE'
        )
        AND NOT EXISTS (
            SELECT 1 FROM user_blocks ub
            WHERE (ub.blocker_id = :myUserId AND ub.blocked_id = u.id)
               OR (ub.blocker_id = u.id AND ub.blocked_id = :myUserId)
        )
        GROUP BY u.id
        ORDER BY dist ASC
    """, countQuery = """
        SELECT count(DISTINCT u.id)
        FROM users u
        INNER JOIN pets p ON u.id = p.owner_user_id
        WHERE u.id != :myUserId
        AND u.is_active = true
        AND u.is_profile_public = true
        AND u.is_location_sharing_enabled = true
        AND ST_DWithin(
            CAST(u.main_location_geom AS geography),
            CAST(ST_SetSRID(ST_MakePoint(:myLng, :myLat), 4326) AS geography),
            :radiusMeters
        )
        AND NOT EXISTS (
            SELECT 1 FROM likes l
            WHERE l.from_user_id = :myUserId
            AND l.to_user_id = u.id
            AND l.status = 'ACTIVE'
        )
        AND NOT EXISTS (
            SELECT 1 FROM user_blocks ub
            WHERE (ub.blocker_id = :myUserId AND ub.blocked_id = u.id)
               OR (ub.blocker_id = u.id AND ub.blocked_id = :myUserId)
        )
    """, nativeQuery = true)
    fun findFriendsNearbyByDistance(
        @Param("myUserId") myUserId: Long,
        @Param("myLat") myLat: Double,
        @Param("myLng") myLng: Double,
        @Param("radiusMeters") radiusMeters: Double,
        pageable: Pageable
    ): Page<User>

    // 홈 추천 전용(reco v2): 반경 무제한 · 이성 우선(소프트) · 신규(위치/펫 없어도) 포함 · 거리순.
    // - ST_DWithin 반경 필터 없음(반경 무제한), pets INNER JOIN 없음(펫 없어도 노출) → GROUP BY 불필요.
    // - List<User> 반환 + Pageable(LIMIT/OFFSET) → countQuery 미실행(홈은 총개수 불필요, 풀카운트 부담 제거).
    // - 정렬: ① 이성 우선(oppositeGender=null 이면 전부 NULL → 무효화) ② 요청자 좌표 有일 때만 거리순(위치 NULL 후보 맨 뒤) ③ 최신 가입순.
    // 주의: PostgreSQL 은 ORDER BY 표현식(CASE 등) 안에서는 SELECT 별칭(dist)을 참조할 수 없으므로
    // ST_Distance 를 CASE 안에 인라인한다(별칭은 단순 정렬키로만 허용됨).
    @Query(value = """
        SELECT u.*
        FROM users u
        WHERE u.id != :myUserId
        AND u.is_active = true
        AND u.is_profile_public = true
        AND u.is_location_sharing_enabled = true
        AND NOT EXISTS (
            SELECT 1 FROM likes l
            WHERE l.from_user_id = :myUserId
            AND l.to_user_id = u.id
            AND l.status = 'ACTIVE'
        )
        AND NOT EXISTS (
            SELECT 1 FROM user_blocks ub
            WHERE (ub.blocker_id = :myUserId AND ub.blocked_id = u.id)
               OR (ub.blocker_id = u.id AND ub.blocked_id = :myUserId)
        )
        ORDER BY (u.gender = :oppositeGender) DESC NULLS LAST,
                 CASE WHEN :hasMyLocation THEN ST_Distance(
                     CAST(u.main_location_geom AS geography),
                     CAST(ST_SetSRID(ST_MakePoint(:myLng, :myLat), 4326) AS geography)
                 ) END ASC NULLS LAST,
                 u.created_at DESC
    """, nativeQuery = true)
    fun findRecommendations(
        @Param("myUserId") myUserId: Long,
        @Param("myLat") myLat: Double,
        @Param("myLng") myLng: Double,
        @Param("oppositeGender") oppositeGender: String?,
        @Param("hasMyLocation") hasMyLocation: Boolean,
        pageable: Pageable
    ): List<User>
}
