package com.goldpet.domain.friend.dto

data class LikeRequest(
    val toUserId: Long,
    /** 좋아요 발생 리스트/정렬 출처(compatible|distance|popular 등) — like_events 기록용. nullable. */
    val source: String? = null
)

data class MatchResponse(
    val matchId: Long,
    val partnerId: Long,
    val partnerNickname: String?,
    val partnerProfileImage: String?,
    val matchedAt: String?
)

data class BlockRequest(
    val blockedUserId: Long
)

data class BlockedUserResponse(
    val blockId: Long,
    val blockedUserId: Long,
    val blockedNickname: String?,
    val blockedProfileImageUrl: String?,
    val blockedPetName: String?,
    val blockedAt: String?
)
