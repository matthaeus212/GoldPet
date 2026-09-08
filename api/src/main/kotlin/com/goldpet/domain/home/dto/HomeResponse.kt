package com.goldpet.domain.home.dto

import com.goldpet.domain.friend.dto.FriendResponse
import java.time.LocalDateTime

data class HomeResponse(
    val recommendations: List<FriendResponse>,
    val recentWalk: HomeRecentWalkResponse?,
    val todayWalk: HomeTodayWalkResponse,
    val notifications: NotificationSummary,
    val myPetNames: List<String> = emptyList()
)

/**
 * 오늘(자정 기준) 누적 산책 통계. 산책 기록이 없어도 0 값으로 반환되며
 * 대표 반려동물 정보(이름/품종/이미지)는 항상 채워진다.
 */
data class HomeTodayWalkResponse(
    val distanceKm: Double,
    val durationMinutes: Long,
    val caloriesBurned: Double,
    val earnedGold: Int,
    val walkCount: Long,
    val petName: String?,
    val petBreed: String?,
    val locationText: String?,
    val petProfileImageUrl: String?,
    val petProfileImageUrlThumbnail: String? = null,
    /** T2: WebP thumbnail for primary pet profile image. */
    val petProfileImageUrlThumbnailWebp: String? = null,
)

data class NotificationSummary(
    val hasUnread: Boolean,
    val count: Long
)

data class HomeRecentWalkResponse(
    val id: Long,
    val distanceKm: Double,
    val durationSeconds: Long,
    val endTime: LocalDateTime,
    val startAddress: String?,
    val petNames: List<String>,
    val petProfileImageUrls: List<String>,
    /** T1-1.5: pet profile images thumbnail list. */
    val petProfileImageUrlsThumbnail: List<String>? = null,
    /** T1-1.5: pet profile images viewer list. */
    val petProfileImageUrlsViewer: List<String>? = null,
    /** T2: WebP thumbnail list for pet profile images. */
    val petProfileImageUrlsThumbnailWebp: List<String?>? = null,
)
