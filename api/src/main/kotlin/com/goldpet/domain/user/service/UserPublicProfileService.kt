package com.goldpet.domain.user.service

import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.common.util.toHttps
import com.goldpet.domain.community.entity.CommunityPost
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.user.dto.PublicUserProfileResponse
import com.goldpet.domain.user.dto.PublicUserProfileResponse.ProfileStatus
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * community-author-profile-gallery Phase 1 §4-1 / §4-3.
 *
 * 공개 프로필 (`GET /api/v1/users/{userId}/public-profile`) 응답 조립 서비스.
 *
 * ## 정책 요약
 * - DEACTIVATED(탈퇴/정지/휴면) 유저 → 404 범용 에러 (존재 은닉).
 * - `target이 viewer 를 차단 (BLOCKED_ME)` → 404 범용 에러 (차단 사실 역추정 방지, 문구 통일).
 * - `viewer가 target 을 차단 (BLOCKED_BY_ME)` → status=BLOCKED_BY_ME, nickname/profileUrl null, postCount=0.
 * - `viewerId == targetUserId` → 정상 응답 + `isMe=true` (프론트 `/mypage` 리다이렉트).
 *
 * ## PII 경계
 * 응답 DTO 는 `@PublicFacingDto`. ArchUnit `PublicFacingDtoArchTest` 가 User/UserAuthProvider 의 PII 필드 접근을 차단.
 * 본 서비스는 nickname / profileImageUrl / variant URL / publicPostCount 만 읽는다.
 */
@Service
@Transactional(readOnly = true)
class UserPublicProfileService(
    private val userRepository: UserRepository,
    private val userBlockRepository: UserBlockRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val fileAttachmentLookupService: FileAttachmentLookupService,
    private val petRepository: com.goldpet.domain.pet.repository.PetRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getPublicProfile(viewerId: Long, targetUserId: Long): PublicUserProfileResponse {
        val isMe = viewerId == targetUserId

        val target = userRepository.findById(targetUserId).orElse(null)
            ?: throw notFound()

        // DEACTIVATED / WITHDRAWN / SUSPENDED / DORMANT — 존재 은닉
        if (target.status != UserStatus.ACTIVE || !target.isActive) {
            throw notFound()
        }

        var blockedByMe = false
        if (!isMe) {
            val targetBlocksViewer =
                userBlockRepository.existsByBlockerIdAndBlockedId(targetUserId, viewerId)
            if (targetBlocksViewer) {
                // BLOCKED_ME — 차단 사실 역추정 방지: 문구/코드 전부 generic 404
                log.debug(
                    "public-profile 404 (blocked_me): viewer={} target={}",
                    viewerId,
                    targetUserId,
                )
                throw notFound()
            }
            blockedByMe =
                userBlockRepository.existsByBlockerIdAndBlockedId(viewerId, targetUserId)
        }

        if (blockedByMe) {
            // 헤더 익명화 — 그리드 영역은 프론트에서 "차단 해제" CTA 로 교체
            return PublicUserProfileResponse(
                userId = target.id,
                nickname = null,
                intro = null,
                profileUrl = null,
                profileUrlThumbnail = null,
                profileUrlViewer = null,
                profileUrls = emptyList(),
                profileUrlsThumbnail = emptyList(),
                profileUrlsViewer = emptyList(),
                publicPostCount = 0L,
                status = ProfileStatus.BLOCKED_BY_ME,
                isMe = false,
                blockedByMe = true,
                pets = emptyList(),
            )
        }

        val publicPostCount = communityPostRepository
            .countByUserIdAndVisibilityAndIsHiddenFalse(target.id, CommunityPost.Visibility.PUBLIC)

        val pets = if (target.hasPet) {
            petRepository.findByOwnerId(target.id).map {
                com.goldpet.domain.pet.dto.PetResponse.from(it, fileAttachmentLookupService)
            }
        } else {
            emptyList()
        }

        val primary = target.profileImageUrl.toHttps()
        val urls = target.profileImages.map { it.imageUrl.toHttps() ?: it.imageUrl }
        return PublicUserProfileResponse(
            userId = target.id,
            nickname = target.nickname,
            intro = target.intro,
            profileUrl = primary,
            profileUrlThumbnail = fileAttachmentLookupService.thumbnailUrlFor(primary),
            profileUrlViewer = fileAttachmentLookupService.viewerUrlFor(primary),
            profileUrls = urls,
            profileUrlsThumbnail = urls.map { fileAttachmentLookupService.thumbnailUrlFor(it) ?: it },
            profileUrlsViewer = urls.map { fileAttachmentLookupService.viewerUrlFor(it) ?: it },
            publicPostCount = publicPostCount,
            status = ProfileStatus.NORMAL,
            isMe = isMe,
            blockedByMe = false,
            pets = pets,
        )
    }

    private fun notFound(): NotFoundException =
        NotFoundException(GENERIC_NOT_FOUND_MESSAGE, "USER_NOT_FOUND_OR_BLOCKED")

    companion object {
        /**
         * 탈퇴·정지·BLOCKED_ME 전부 동일 문구 — 차단 사실 역추정 방지 (Plan §4-3).
         * 프론트에서도 이 문자열을 매칭하지 않고 status code(404) 기반으로 분기해야 한다.
         */
        const val GENERIC_NOT_FOUND_MESSAGE: String = "프로필을 불러올 수 없어요"
    }
}
