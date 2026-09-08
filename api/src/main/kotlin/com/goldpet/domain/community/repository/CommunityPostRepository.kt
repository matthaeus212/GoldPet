package com.goldpet.domain.community.repository

import com.goldpet.domain.community.entity.CommunityCategory
import com.goldpet.domain.community.entity.CommunityComment
import com.goldpet.domain.community.entity.CommunityPost
import com.goldpet.domain.community.entity.CommunityPost.Visibility
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CommunityPostRepository : JpaRepository<CommunityPost, Long> {
    fun findAllByCategory(category: CommunityCategory, pageable: Pageable): Page<CommunityPost>
    fun countByCategory(category: CommunityCategory): Long

    // Search methods
    fun findByTitleContainingIgnoreCase(title: String, pageable: Pageable): Page<CommunityPost>

    @Query("SELECT p FROM CommunityPost p WHERE p.title LIKE %:keyword% OR p.content LIKE %:keyword%")
    fun searchByKeyword(@Param("keyword") keyword: String, pageable: Pageable): Page<CommunityPost>

    // User post count
    fun countByUserId(userId: Long): Long

    /**
     * community-author-profile-gallery §4-1 — public profile 헤더의 `publicPostCount`.
     *
     * 조건: `user_id = :userId AND visibility = :visibility AND is_hidden = false`.
     * V58 의 `idx_community_posts_author_visibility_created` 로 커버된다.
     */
    fun countByUserIdAndVisibilityAndIsHiddenFalse(userId: Long, visibility: Visibility): Long

    // Adopted comment lookup
    fun findByAdoptedComment(adoptedComment: CommunityComment): CommunityPost?

    // User's posts
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<CommunityPost>
    fun findAllByUserId(userId: Long): List<CommunityPost>

    // Visible posts
    override fun findAll(pageable: Pageable): Page<CommunityPost>

    // Block-filtered queries
    @Query("SELECT p FROM CommunityPost p WHERE p.category = :category AND p.user.id NOT IN :blockedUserIds")
    fun findAllByCategoryExcludingUsers(
        @Param("category") category: CommunityCategory,
        @Param("blockedUserIds") blockedUserIds: List<Long>,
        pageable: Pageable
    ): Page<CommunityPost>

    @Query("SELECT p FROM CommunityPost p WHERE p.user.id NOT IN :blockedUserIds")
    fun findAllExcludingUsers(
        @Param("blockedUserIds") blockedUserIds: List<Long>,
        pageable: Pageable
    ): Page<CommunityPost>

    @Query("SELECT p FROM CommunityPost p WHERE (p.title LIKE %:keyword% OR p.content LIKE %:keyword%) AND p.user.id NOT IN :blockedUserIds")
    fun searchByKeywordExcludingUsers(
        @Param("keyword") keyword: String,
        @Param("blockedUserIds") blockedUserIds: List<Long>,
        pageable: Pageable
    ): Page<CommunityPost>

    // User-facing filtered versions (isHidden = false)
    fun findAllByCategoryAndIsHiddenFalse(category: CommunityCategory, pageable: Pageable): Page<CommunityPost>
    fun countByCategoryAndIsHiddenFalse(category: CommunityCategory): Long
    fun findAllByIsHiddenFalse(pageable: Pageable): Page<CommunityPost>

    @Query("SELECT p FROM CommunityPost p WHERE p.isHidden = false AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    fun searchByKeywordAndIsHiddenFalse(@Param("keyword") keyword: String, pageable: Pageable): Page<CommunityPost>

    @Query("SELECT p FROM CommunityPost p WHERE p.isHidden = false AND LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))")
    fun findByTitleContainingIgnoreCaseAndIsHiddenFalse(@Param("title") title: String, pageable: Pageable): Page<CommunityPost>

    // ExcludingUsers + isHidden filtered versions
    @Query("SELECT p FROM CommunityPost p WHERE p.isHidden = false AND p.category = :category AND p.user.id NOT IN :blockedUserIds")
    fun findAllByCategoryExcludingUsersAndIsHiddenFalse(
        @Param("category") category: CommunityCategory,
        @Param("blockedUserIds") blockedUserIds: List<Long>,
        pageable: Pageable
    ): Page<CommunityPost>

    @Query("SELECT p FROM CommunityPost p WHERE p.isHidden = false AND p.user.id NOT IN :blockedUserIds")
    fun findAllExcludingUsersAndIsHiddenFalse(
        @Param("blockedUserIds") blockedUserIds: List<Long>,
        pageable: Pageable
    ): Page<CommunityPost>

    @Query("SELECT p FROM CommunityPost p WHERE p.isHidden = false AND p.user.id NOT IN :blockedUserIds AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    fun searchByKeywordExcludingUsersAndIsHiddenFalse(
        @Param("keyword") keyword: String,
        @Param("blockedUserIds") blockedUserIds: List<Long>,
        pageable: Pageable
    ): Page<CommunityPost>

    /**
     * community-author-profile-gallery §4-1 — 작성자별 공개 게시글 cursor pagination.
     *
     * - 필터: `user_id = :authorId AND visibility = PUBLIC AND isHidden = false`.
     * - 차단 관계(viewer↔author 양방향) 는 `blockedUserIds` 로 상위에서 해결해 전달. 비어 있으면
     *   호출자가 sentinel `listOf(-1L)` 등 비어있지 않은 list 를 넘겨야 한다 (JPQL IN () 회피).
     * - Cursor tuple `(createdAt DESC, id DESC)` — 동일 `createdAt` 을 가진 row 의 결정적 순서 보장.
     *   첫 페이지는 `cursorCreatedAt = LocalDateTime.MAX`, `cursorId = Long.MAX_VALUE` 를 넘긴다.
     * - `LEFT JOIN FETCH p.images cpi LEFT JOIN FETCH cpi.file fa` 로 N+1 제거.
     *   `DISTINCT` 로 row 폭발 완화 (in-memory deduplication, Hibernate HHH000104 경고는 수용 — gallery 특성상 이미지 수 한정).
     * - `pageable` 은 `Pageable.ofSize(size + 1)` 로 넘겨 "다음 페이지 존재" 판정 가능하게 한다.
     *
     * V58 인덱스 `idx_community_posts_author_visibility_created` 에 의해 커버된다.
     */
    @Query(
        """
        SELECT DISTINCT p FROM CommunityPost p
          LEFT JOIN FETCH p.images cpi
          LEFT JOIN FETCH cpi.file fa
        WHERE p.user.id = :authorId
          AND p.visibility = :visibility
          AND p.isHidden = false
          AND p.user.id NOT IN :blockedUserIds
          AND (
            p.createdAt < :cursorCreatedAt
            OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId)
          )
        ORDER BY p.createdAt DESC, p.id DESC
        """,
    )
    fun findPublicByAuthorWithCursor(
        @Param("authorId") authorId: Long,
        @Param("visibility") visibility: Visibility,
        @Param("cursorCreatedAt") cursorCreatedAt: LocalDateTime,
        @Param("cursorId") cursorId: Long,
        @Param("blockedUserIds") blockedUserIds: List<Long>,
        pageable: Pageable,
    ): List<CommunityPost>

    /**
     * 트렌딩(인기) 랭킹 — 시간 감쇠 hot score.
     *
     * score = (like*wLike + view*wView + comment*wComment) / (ageHours + 2)^gravity
     * - 최근 [since] 이후 글만 후보(오래된 글 영구 상단 방지).
     * - is_hidden=false, 카테고리(nullable), 차단 유저 제외.
     * - `blockedUserIds` 는 비어있지 않은 list 를 넘긴다(JPQL/SQL IN () 회피, sentinel `listOf(0L)`).
     * - Pageable 은 **unsorted** 로 넘긴다(여기 ORDER BY 와 충돌 방지).
     */
    @Query(
        value = """
            SELECT * FROM community_posts p
            WHERE p.is_hidden = false
              AND p.created_at >= :since
              AND (CAST(:categoryId AS bigint) IS NULL OR p.category_id = :categoryId)
              AND p.user_id NOT IN (:blockedUserIds)
            ORDER BY (
              (p.like_count * :wLike + p.view_count * :wView
                + COALESCE((SELECT COUNT(*) FROM community_comments c
                            WHERE c.post_id = p.id AND c.is_hidden = false), 0) * :wComment)
              / POWER(EXTRACT(EPOCH FROM (now() - p.created_at)) / 3600.0 + 2, :gravity)
            ) DESC
        """,
        countQuery = """
            SELECT COUNT(*) FROM community_posts p
            WHERE p.is_hidden = false
              AND p.created_at >= :since
              AND (CAST(:categoryId AS bigint) IS NULL OR p.category_id = :categoryId)
              AND p.user_id NOT IN (:blockedUserIds)
        """,
        nativeQuery = true,
    )
    fun findTrending(
        @Param("categoryId") categoryId: Long?,
        @Param("blockedUserIds") blockedUserIds: List<Long>,
        @Param("since") since: LocalDateTime,
        @Param("wLike") wLike: Double,
        @Param("wComment") wComment: Double,
        @Param("wView") wView: Double,
        @Param("gravity") gravity: Double,
        pageable: Pageable,
    ): Page<CommunityPost>
}
