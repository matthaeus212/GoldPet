package com.goldpet.domain.user.dto

import com.goldpet.common.annotation.PublicFacingDto

/**
 * Public-facing view of another user's community profile.
 *
 * Strictly limited to fields already visible on community posts (nickname +
 * avatar + image variants). PII fields (phone, email, birth date, gender,
 * real name, fcmToken, geo, OAuth provider data, profile lock timestamps)
 * MUST NOT be added — enforced by `PublicFacingDtoArchTest`.
 *
 * `BLOCKED_ME` / `DEACTIVATED` cases collapse to 404 at the controller layer
 * (차단 사실 역추정 방지). Therefore only `NORMAL` and `BLOCKED_BY_ME` are
 * emitted here.
 */
@PublicFacingDto
data class PublicUserProfileResponse(
    val userId: Long,
    val nickname: String?,
    val intro: String?,
    val profileUrl: String?,
    val profileUrlThumbnail: String?,
    val profileUrlViewer: String?,
    /** 복수 프로필 이미지 — 원본 URL 리스트. */
    val profileUrls: List<String> = emptyList(),
    /** 복수 프로필 이미지 — 썸네일 variant 리스트. */
    val profileUrlsThumbnail: List<String> = emptyList(),
    /** 복수 프로필 이미지 — viewer variant 리스트. */
    val profileUrlsViewer: List<String> = emptyList(),
    val publicPostCount: Long,
    val status: ProfileStatus,
    val isMe: Boolean,
    val blockedByMe: Boolean,
    val pets: List<com.goldpet.domain.pet.dto.PetResponse> = emptyList(),
) {
    enum class ProfileStatus {
        /** 일반 열람 가능. */
        NORMAL,

        /** 내가 차단한 사용자 — 헤더는 익명화(nickname/profileUrl null), 그리드는 "차단 해제" CTA 로만 노출. */
        BLOCKED_BY_ME,

        /** 탈퇴/정지 — 실제로는 404 로 통일되지만, 내부 경로/테스트 용도로 enum 유지. */
        DEACTIVATED,
    }
}
