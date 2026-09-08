package com.goldpet.domain.user.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.common.util.toHttps
import com.goldpet.domain.user.entity.User

data class UserResponse(
    val id: Long,
    val email: String?,
    val nickname: String?,
    val gender: String?,
    val birthYear: Int?,
    val mainLocationText: String?,
    val mainLocationLat: Double?,
    val mainLocationLng: Double?,
    val profileImageUrl: String?,
    val profileImageUrls: List<String>,
    /** T1-1.2: viewer variant (1600px, ~150KB). Null when variant lookup skipped. */
    val profileImageUrlViewer: String? = null,
    /** T1-1.2: thumbnail variant (200px). */
    val profileImageUrlThumbnail: String? = null,
    /** T1-1.2: viewer variant list matching profileImageUrls order. */
    val profileImageUrlsViewer: List<String>? = null,
    /** T1-1.2: thumbnail variant list matching profileImageUrls order. */
    val profileImageUrlsThumbnail: List<String>? = null,
    /** T2: WebP thumbnail variant for primary profile image. Null for pre-WebP uploads. */
    val profileImageUrlThumbnailWebp: String? = null,
    /** T2: WebP thumbnail variant list matching profileImageUrls order. */
    val profileImageUrlsThumbnailWebp: List<String?>? = null,
    val name: String?,
    val birthDate: String?,
    val phoneNumber: String?,
    val hasPet: Boolean,
    val intro: String?,
    val mbti: String?,
    val interests: List<String>,
    val hobbies: List<String>,
    val goldBalance: Int,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val oauthProvider: String,
    val isProfileLocked: Boolean,
    // 온보딩 완료 단일 기준. self(from)에서만 실제 boolean, 타 사용자 공개(publicFrom)에선 null(해당없음).
    val signupCompleted: Boolean? = null
) {
    companion object {
        /**
         * @param variantResolver T1-1.1 FileAttachmentLookupService. null 이면 variant 필드 미공급.
         *        리스트 경로에서는 호출 전에 `variantResolver.batchLookup(allUrls)` 로 Caffeine warm 필수.
         */
        fun from(user: User, variantResolver: FileAttachmentLookupService? = null): UserResponse {
            val primary = user.profileImageUrl.toHttps()
            val urls = user.profileImages.map { it.imageUrl.toHttps() ?: it.imageUrl }
            return UserResponse(
                id = user.id,
                email = user.email,
                nickname = user.nickname,
                gender = user.gender,
                birthYear = user.birthYear ?: user.birthDate?.year,
                mainLocationText = user.mainLocationText,
                mainLocationLat = user.mainLocationGeom?.y, // Latitude
                mainLocationLng = user.mainLocationGeom?.x, // Longitude
                profileImageUrl = primary,
                profileImageUrls = urls,
                profileImageUrlViewer = variantResolver?.viewerUrlFor(primary),
                profileImageUrlThumbnail = variantResolver?.thumbnailUrlFor(primary),
                profileImageUrlsViewer = variantResolver?.let { r -> urls.map { r.viewerUrlFor(it) ?: it } },
                profileImageUrlsThumbnail = variantResolver?.let { r -> urls.map { r.thumbnailUrlFor(it) ?: it } },
                profileImageUrlThumbnailWebp = variantResolver?.thumbnailUrlWebpFor(primary),
                profileImageUrlsThumbnailWebp = variantResolver?.let { r -> urls.map { r.thumbnailUrlWebpFor(it) } },
                name = user.name,
                birthDate = user.birthDate?.toString(),
                phoneNumber = user.phoneNumber,
                hasPet = user.hasPet,
                intro = user.intro,
                mbti = user.mbti,
                interests = user.interests.map { it.name },
                hobbies = user.hobbies.map { it.name },
                goldBalance = user.goldBalance,
                isActive = user.isActive,
                oauthProvider = user.oauthProvider,
                isProfileLocked = user.profileLockedAt != null,
                signupCompleted = user.signupCompletedAt != null
            )
        }

        /** Public view of another user's profile. Hides PII and precise location. */
        fun publicFrom(user: User, variantResolver: FileAttachmentLookupService? = null): UserResponse {
            val primary = user.profileImageUrl.toHttps()
            val urls = user.profileImages.map { it.imageUrl.toHttps() ?: it.imageUrl }
            return UserResponse(
                id = user.id,
                email = null,
                nickname = user.nickname,
                gender = user.gender,
                birthYear = null,
                mainLocationText = user.mainLocationText,
                mainLocationLat = null,
                mainLocationLng = null,
                profileImageUrl = primary,
                profileImageUrls = urls,
                profileImageUrlViewer = variantResolver?.viewerUrlFor(primary),
                profileImageUrlThumbnail = variantResolver?.thumbnailUrlFor(primary),
                profileImageUrlsViewer = variantResolver?.let { r -> urls.map { r.viewerUrlFor(it) ?: it } },
                profileImageUrlsThumbnail = variantResolver?.let { r -> urls.map { r.thumbnailUrlFor(it) ?: it } },
                profileImageUrlThumbnailWebp = variantResolver?.thumbnailUrlWebpFor(primary),
                profileImageUrlsThumbnailWebp = variantResolver?.let { r -> urls.map { r.thumbnailUrlWebpFor(it) } },
                name = null,
                birthDate = null,
                phoneNumber = null,
                hasPet = user.hasPet,
                intro = user.intro,
                mbti = user.mbti,
                interests = user.interests.map { it.name },
                hobbies = user.hobbies.map { it.name },
                goldBalance = 0,
                isActive = user.isActive,
                oauthProvider = user.oauthProvider,
                isProfileLocked = user.profileLockedAt != null,
                // 타 사용자의 온보딩 상태는 공개 계약에 포함하지 않음 → null(해당없음).
                signupCompleted = null
            )
        }
    }
}
