package com.goldpet.domain.walk.repository

import com.goldpet.domain.walk.entity.WalkSpot
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface WalkSpotRepository : JpaRepository<WalkSpot, Long> {

    @Query("""
        SELECT ws FROM WalkSpot ws
        JOIN FETCH ws.walk w
        WHERE w.user.id = :userId
        AND ws.type = com.goldpet.domain.walk.entity.WalkSpotType.PHOTO
        AND ws.imageUrl IS NOT NULL
        ORDER BY ws.timestamp DESC
    """)
    fun findPhotoSpotsByUserId(
        @Param("userId") userId: Long,
        pageable: Pageable
    ): Page<WalkSpot>

    @Query("""
        SELECT ws FROM WalkSpot ws
        JOIN FETCH ws.walk w
        WHERE w.user.id = :userId
        AND ws.type = com.goldpet.domain.walk.entity.WalkSpotType.PHOTO
        AND ws.imageUrl IS NOT NULL
        AND ws.timestamp >= :start AND ws.timestamp < :end
        ORDER BY ws.timestamp DESC
    """)
    fun findPhotoSpotsByUserIdAndMonth(
        @Param("userId") userId: Long,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime,
        pageable: Pageable
    ): Page<WalkSpot>

    @Query("""
        SELECT ws FROM WalkSpot ws
        JOIN FETCH ws.walk w
        WHERE w.isPublic = true
        AND ws.type = com.goldpet.domain.walk.entity.WalkSpotType.PHOTO
        AND ws.imageUrl IS NOT NULL
        AND ws.hiddenFromPublic = false
        AND w.user.id NOT IN :blockedUserIds
        ORDER BY ws.timestamp DESC
    """)
    fun findPublicPhotoSpots(
        @Param("blockedUserIds") blockedUserIds: List<Long>,
        pageable: Pageable
    ): Page<WalkSpot>

    /**
     * community-author-profile-gallery Phase 2 F1 — 특정 유저의 공개 walk 사진을 cursor pagination 으로 조회.
     *
     * Phase 1 `CommunityAuthorPostsService` 와 동일한 `(timestamp DESC, id DESC)` tuple cursor + OR-expanded 비교.
     * JOIN FETCH ws.walk → w.user 로 N+1 방지. hiddenFromPublic/공개여부/차단 필터는 기존 `findPublicPhotoSpots` 와 동일.
     */
    @Query("""
        SELECT ws FROM WalkSpot ws
        JOIN FETCH ws.walk w
        JOIN FETCH w.user u
        WHERE u.id = :authorId
        AND w.isPublic = true
        AND ws.type = com.goldpet.domain.walk.entity.WalkSpotType.PHOTO
        AND ws.imageUrl IS NOT NULL
        AND ws.hiddenFromPublic = false
        AND u.id NOT IN :blockedUserIds
        AND (ws.timestamp < :cursorTimestamp
             OR (ws.timestamp = :cursorTimestamp AND ws.id < :cursorId))
        ORDER BY ws.timestamp DESC, ws.id DESC
    """)
    fun findPublicPhotoSpotsByAuthorWithCursor(
        @Param("authorId") authorId: Long,
        @Param("blockedUserIds") blockedUserIds: List<Long>,
        @Param("cursorTimestamp") cursorTimestamp: LocalDateTime,
        @Param("cursorId") cursorId: Long,
        pageable: Pageable,
    ): List<WalkSpot>

    // viewer 또는 medium 이 없으면 대상. 구 사진은 예전 워커가 thumb/viewer 만 만들어 medium 이 비어 있어
    // (파생 키 404 → 큰 사진 blank) 여기서 다시 잡아 medium 을 채운다.
    @Query("""
        SELECT ws FROM WalkSpot ws
        WHERE ws.type = com.goldpet.domain.walk.entity.WalkSpotType.PHOTO
        AND ws.imageUrl IS NOT NULL
        AND (ws.imageKeyViewer IS NULL OR ws.imageKeyMedium IS NULL)
        ORDER BY ws.id ASC
    """)
    fun findSpotsWithoutVariantKeys(pageable: Pageable): Page<WalkSpot>

    @Query("""
        SELECT ws FROM WalkSpot ws
        WHERE ws.type = com.goldpet.domain.walk.entity.WalkSpotType.PHOTO
        AND ws.imageUrl IS NOT NULL
        AND (ws.imageKeyViewer IS NULL OR ws.imageKeyMedium IS NULL)
        AND ws.createdAt < :before
        ORDER BY ws.id ASC
    """)
    fun findSpotsForSweep(
        @Param("before") before: LocalDateTime,
        pageable: Pageable
    ): List<WalkSpot>
}
