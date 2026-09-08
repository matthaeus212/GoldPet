package com.goldpet.domain.community.dto

import com.goldpet.common.annotation.PublicFacingDto
import java.time.LocalDateTime

/**
 * community-author-profile-gallery §4-1 — `/users/{userId}/community/posts` 응답 row.
 *
 * 갤러리 타일용 최소 스키마. 타일 탭 시 `ImageGalleryModal` 재사용하므로 여기서는 variant URL 만 전달.
 * 본문/댓글/like 등 상세 필드는 포함하지 않는다 (Phase 1 scope).
 */
@PublicFacingDto
data class CommunityPostSummaryDto(
    val id: Long,
    val createdAt: LocalDateTime,
    val thumbnailUrl: String?,
    val mediumUrl: String?,
    val viewerUrl: String?,
    val imageCount: Int,
)

/**
 * Cursor page wrapper. `nextCursor == null` ⇔ 마지막 페이지.
 * cursor payload: base64url(`{createdAt_epoch_ms}:{id}`).
 */
@PublicFacingDto
data class AuthorPostsPage(
    val posts: List<CommunityPostSummaryDto>,
    val nextCursor: String?,
)
