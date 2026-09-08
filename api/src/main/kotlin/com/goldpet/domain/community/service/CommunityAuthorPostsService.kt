package com.goldpet.domain.community.service

import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.common.util.toHttps
import com.goldpet.domain.community.dto.AuthorPostsPage
import com.goldpet.domain.community.dto.CommunityPostSummaryDto
import com.goldpet.domain.community.entity.CommunityPost
import com.goldpet.domain.community.entity.CommunityPostImage
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64

/**
 * community-author-profile-gallery §4-1 — 작성자별 공개 게시글 cursor pagination.
 *
 * - `GET /api/v1/users/{userId}/community/posts?cursor=...&size=30` 백엔드.
 * - 차단 관계(viewer↔author 양방향)는 `UserBlockRepository.findBlockedUserIds(viewerId)` 가 이미
 *   native UNION 으로 양방향 반환 (`UserBlockRepository.kt:16-22`). 신규 헬퍼 불필요.
 * - target 유저가 viewer 를 차단한 경우도 같은 세트에 포함되므로 `NOT IN` 필터 한 방에 해결.
 * - target 유저 자체가 DEACTIVATED/WITHDRAWN 이면 `UserPublicProfileService` 와 동일하게 404 통일.
 */
@Service
@Transactional(readOnly = true)
class CommunityAuthorPostsService(
    private val userRepository: UserRepository,
    private val userBlockRepository: UserBlockRepository,
    private val communityPostRepository: CommunityPostRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun findByAuthorId(
        viewerId: Long,
        authorId: Long,
        cursor: String?,
        size: Int = DEFAULT_SIZE,
    ): AuthorPostsPage {
        if (size < 1) throw BadRequestException("size must be >= 1", "INVALID_PAGE_SIZE")
        val effectiveSize = size.coerceAtMost(MAX_SIZE)

        // 존재 은닉 — UserPublicProfileService 와 문구/코드 일치.
        val author = userRepository.findById(authorId).orElse(null)
            ?: throw NotFoundException(GENERIC_NOT_FOUND_MESSAGE, "USER_NOT_FOUND_OR_BLOCKED")
        if (author.status != UserStatus.ACTIVE || !author.isActive) {
            throw NotFoundException(GENERIC_NOT_FOUND_MESSAGE, "USER_NOT_FOUND_OR_BLOCKED")
        }

        // target→viewer 차단 시 갤러리 노출 금지 (BLOCKED_ME 는 404 로 통일).
        if (viewerId != authorId &&
            userBlockRepository.existsByBlockerIdAndBlockedId(authorId, viewerId)
        ) {
            throw NotFoundException(GENERIC_NOT_FOUND_MESSAGE, "USER_NOT_FOUND_OR_BLOCKED")
        }

        val (cursorCreatedAt, cursorId) = decodeCursor(cursor)

        // Native UNION 양방향 차단 필터 재사용. 비어있으면 JPQL IN () 회피용 sentinel 1건.
        val blockedIds = userBlockRepository.findBlockedUserIds(viewerId)
            .ifEmpty { listOf(SENTINEL_BLOCKED_ID) }

        // size+1 fetch 로 다음 페이지 존재 판정. JOIN FETCH + DISTINCT 로 N+1 제거.
        val fetched = communityPostRepository.findPublicByAuthorWithCursor(
            authorId = authorId,
            visibility = CommunityPost.Visibility.PUBLIC,
            cursorCreatedAt = cursorCreatedAt,
            cursorId = cursorId,
            blockedUserIds = blockedIds,
            pageable = PageRequest.ofSize(effectiveSize + 1),
        )

        val hasMore = fetched.size > effectiveSize
        val pageRows = if (hasMore) fetched.take(effectiveSize) else fetched
        val nextCursor = if (hasMore) {
            val last = pageRows.last()
            encodeCursor(last.createdAt, last.id)
        } else {
            null
        }

        return AuthorPostsPage(
            posts = pageRows.map { it.toSummary() },
            nextCursor = nextCursor,
        )
    }

    private fun CommunityPost.toSummary(): CommunityPostSummaryDto {
        val firstImage: CommunityPostImage? = images.minByOrNull { it.sortOrder }
        val file = firstImage?.file
        val origin = file?.url.toHttps()
        return CommunityPostSummaryDto(
            id = id,
            createdAt = createdAt,
            // variant 없으면 원본 URL fallback (Plan §4-1 — backfill gate 미달 시 원본 정상 동작 보장).
            thumbnailUrl = file?.thumbnailUrl.toHttps() ?: origin,
            mediumUrl = file?.mediumUrl.toHttps() ?: origin,
            viewerUrl = file?.viewerUrl.toHttps() ?: origin,
            imageCount = images.size,
        )
    }

    private fun decodeCursor(cursor: String?): Pair<LocalDateTime, Long> {
        if (cursor.isNullOrBlank()) {
            // 첫 페이지 — tuple 비교 `(createdAt, id) < (MAX, MAX)` 는 항상 true.
            return LocalDateTime.of(9999, 12, 31, 23, 59, 59) to Long.MAX_VALUE
        }
        return try {
            val raw = String(
                Base64.getUrlDecoder().decode(cursor.toByteArray(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8,
            )
            val parts = raw.split(':')
            require(parts.size == 2) { "cursor payload must be 'epochMs:id'" }
            val epochMs = parts[0].toLong()
            val id = parts[1].toLong()
            require(id >= 0) { "cursor id must be >= 0" }
            val ts = LocalDateTime.ofEpochSecond(epochMs / 1000, ((epochMs % 1000) * 1_000_000).toInt(), ZoneOffset.UTC)
            ts to id
        } catch (e: Exception) {
            log.debug("invalid cursor '{}': {}", cursor, e.message)
            throw BadRequestException("invalid cursor", "INVALID_CURSOR")
        }
    }

    private fun encodeCursor(createdAt: LocalDateTime, id: Long): String {
        val epochMs = createdAt.toEpochSecond(ZoneOffset.UTC) * 1000 +
            createdAt.nano / 1_000_000
        val raw = "$epochMs:$id"
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    companion object {
        const val DEFAULT_SIZE: Int = 30
        const val MAX_SIZE: Int = 100

        /** BLOCKED_ME / DEACTIVATED 전부 동일 문구 — 차단 사실 역추정 방지. */
        const val GENERIC_NOT_FOUND_MESSAGE: String = "프로필을 불러올 수 없어요"

        /** JPQL `p.user.id NOT IN (:blockedUserIds)` 가 빈 list 를 받지 못하는 문제 회피 sentinel. 실제 user.id 와 충돌 불가. */
        private const val SENTINEL_BLOCKED_ID: Long = -1L
    }
}
