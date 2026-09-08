package com.goldpet.domain.course.repository

import com.goldpet.domain.course.entity.CourseDifficulty
import com.goldpet.domain.course.entity.WalkCourse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface WalkCourseRepository : JpaRepository<WalkCourse, Long> {

    fun findByAuthorIdOrderByCreatedAtDesc(authorId: Long, pageable: Pageable): Page<WalkCourse>

    @Query("""
        SELECT wc FROM WalkCourse wc JOIN FETCH wc.author
        WHERE wc.isPublished = true
        AND (:province IS NULL OR wc.province = :province)
        AND (:difficulty IS NULL OR wc.difficulty = :difficulty)
        ORDER BY (wc.likeCount * 2 + wc.walkCount * 3 + wc.rating * 10 + wc.commentCount) DESC
    """, countQuery = """
        SELECT COUNT(wc) FROM WalkCourse wc
        WHERE wc.isPublished = true
        AND (:province IS NULL OR wc.province = :province)
        AND (:difficulty IS NULL OR wc.difficulty = :difficulty)
    """)
    fun findPopularCourses(
        @Param("province") province: String?,
        @Param("difficulty") difficulty: CourseDifficulty?,
        pageable: Pageable
    ): Page<WalkCourse>

    @Query("""
        SELECT wc FROM WalkCourse wc JOIN FETCH wc.author
        WHERE wc.isPublished = true
        AND (:province IS NULL OR wc.province = :province)
        AND (:difficulty IS NULL OR wc.difficulty = :difficulty)
        ORDER BY wc.createdAt DESC
    """, countQuery = """
        SELECT COUNT(wc) FROM WalkCourse wc
        WHERE wc.isPublished = true
        AND (:province IS NULL OR wc.province = :province)
        AND (:difficulty IS NULL OR wc.difficulty = :difficulty)
    """)
    fun findLatestCourses(
        @Param("province") province: String?,
        @Param("difficulty") difficulty: CourseDifficulty?,
        pageable: Pageable
    ): Page<WalkCourse>

    @Query("""
        SELECT wc FROM WalkCourse wc JOIN FETCH wc.author
        WHERE wc.isPublished = true
        AND (:province IS NULL OR wc.province = :province)
        AND (:difficulty IS NULL OR wc.difficulty = :difficulty)
        ORDER BY wc.distanceKm ASC
    """, countQuery = """
        SELECT COUNT(wc) FROM WalkCourse wc
        WHERE wc.isPublished = true
        AND (:province IS NULL OR wc.province = :province)
        AND (:difficulty IS NULL OR wc.difficulty = :difficulty)
    """)
    fun findByDistanceCourses(
        @Param("province") province: String?,
        @Param("difficulty") difficulty: CourseDifficulty?,
        pageable: Pageable
    ): Page<WalkCourse>

    @Query("""
        SELECT wc FROM WalkCourse wc JOIN FETCH wc.author
        WHERE wc.isPublished = true
        AND (:province IS NULL OR wc.province = :province)
        AND (:difficulty IS NULL OR wc.difficulty = :difficulty)
        ORDER BY wc.rating DESC, wc.ratingCount DESC
    """, countQuery = """
        SELECT COUNT(wc) FROM WalkCourse wc
        WHERE wc.isPublished = true
        AND (:province IS NULL OR wc.province = :province)
        AND (:difficulty IS NULL OR wc.difficulty = :difficulty)
    """)
    fun findByRatingCourses(
        @Param("province") province: String?,
        @Param("difficulty") difficulty: CourseDifficulty?,
        pageable: Pageable
    ): Page<WalkCourse>

    @Query(value = """
        SELECT * FROM walk_courses
        WHERE ST_DWithin(
            start_location::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            :radiusMeters
        )
        AND is_published = true
        ORDER BY (like_count * 2 + walk_count * 3 + rating * 10 + comment_count) DESC
    """, countQuery = """
        SELECT COUNT(*) FROM walk_courses
        WHERE ST_DWithin(
            start_location::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            :radiusMeters
        )
        AND is_published = true
    """, nativeQuery = true)
    fun findNearbyPopularCourses(
        @Param("lng") lng: Double,
        @Param("lat") lat: Double,
        @Param("radiusMeters") radiusMeters: Double,
        pageable: Pageable
    ): Page<WalkCourse>

    @Query(value = """
        SELECT wc.* FROM walk_courses wc JOIN users a ON a.id = wc.author_id
        WHERE (CAST(:search AS TEXT) IS NULL OR LOWER(wc.title) LIKE LOWER('%' || CAST(:search AS TEXT) || '%'))
        AND (CAST(:difficulty AS TEXT) IS NULL OR wc.difficulty = CAST(:difficulty AS TEXT))
        AND (:isPublished IS NULL OR wc.is_published = :isPublished)
    """, countQuery = """
        SELECT COUNT(*) FROM walk_courses wc
        WHERE (CAST(:search AS TEXT) IS NULL OR LOWER(wc.title) LIKE LOWER('%' || CAST(:search AS TEXT) || '%'))
        AND (CAST(:difficulty AS TEXT) IS NULL OR wc.difficulty = CAST(:difficulty AS TEXT))
        AND (:isPublished IS NULL OR wc.is_published = :isPublished)
    """, nativeQuery = true)
    fun findAllForAdmin(
        @Param("search") search: String?,
        @Param("difficulty") difficulty: String?,
        @Param("isPublished") isPublished: Boolean?,
        pageable: Pageable
    ): Page<WalkCourse>

    @Transactional
    @Modifying
    @Query("UPDATE walk_courses SET walk_count = walk_count + 1 WHERE id = :courseId", nativeQuery = true)
    fun incrementWalkCount(@Param("courseId") courseId: Long)

    @Transactional
    @Modifying
    @Query("UPDATE walk_courses SET like_count = like_count + :delta WHERE id = :courseId", nativeQuery = true)
    fun updateLikeCount(@Param("courseId") courseId: Long, @Param("delta") delta: Int)

    @Transactional
    @Modifying
    @Query("UPDATE walk_courses SET comment_count = comment_count + :delta WHERE id = :courseId", nativeQuery = true)
    fun updateCommentCount(@Param("courseId") courseId: Long, @Param("delta") delta: Int)

    @Transactional
    @Modifying
    @Query("""
        UPDATE walk_courses
        SET rating = (rating * rating_count + :newRating) / (rating_count + 1),
            rating_count = rating_count + 1
        WHERE id = :courseId
    """, nativeQuery = true)
    fun addRating(@Param("courseId") courseId: Long, @Param("newRating") newRating: Double)
}
