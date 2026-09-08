package com.goldpet.domain.home.service

import com.goldpet.domain.common.exception.*
import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.home.dto.HomeRecentWalkResponse
import com.goldpet.domain.home.dto.HomeResponse
import com.goldpet.domain.home.dto.HomeTodayWalkResponse
import com.goldpet.domain.home.dto.NotificationSummary
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.friend.service.FriendService
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.repository.GoldTransactionRepository
import com.goldpet.domain.walk.repository.WalkRepository
import com.goldpet.domain.notification.service.NotificationService
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.pet.repository.PetRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class HomeService(
    private val friendService: FriendService,
    private val walkRepository: WalkRepository,
    private val goldTransactionRepository: GoldTransactionRepository,
    private val notificationService: NotificationService,
    private val userRepository: UserRepository,
    private val petRepository: PetRepository,
    private val fileAttachmentLookupService: FileAttachmentLookupService,
    private val systemSettingService: SystemSettingService
) {
    @Transactional(readOnly = true)
    fun getHomeData(userId: Long): HomeResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }

        // Get user location (default to Seoul if not set)
        val lat = user.mainLocationGeom?.y ?: 37.5665
        val lng = user.mainLocationGeom?.x ?: 126.978

        // Get friend recommendations (top 10).
        // reco.recommendation_v2.enabled ON → 반경무제한·이성우선·신규완화·거리순 (신규 전용 경로).
        // OFF(기본) → 기존 10km 거리순 경로 유지(즉시 롤백 가능).
        val recommendations = if (systemSettingService.getBoolean("reco.recommendation_v2.enabled", false)) {
            val oppositeGender = when (user.gender) {
                "MALE" -> "FEMALE"
                "FEMALE" -> "MALE"
                else -> null // gender NULL → 이성 CASE 무효화 → 순수 거리순 자연 폴백
            }
            friendService.getRecommendations(
                myUserId = userId,
                myLat = lat,
                myLng = lng,
                oppositeGender = oppositeGender,
                hasMyLocation = user.mainLocationGeom != null,
                limit = 10
            )
        } else {
            friendService.getFriends(
                myUserId = userId,
                myLat = lat,
                myLng = lng,
                distanceKm = 10.0, // Home: 10km radius
                sortBy = "distance",
                petTypes = null,
                genders = null,
                pageable = PageRequest.of(0, 10)
            ).content
        }

        // Get most recent walk (single query, no full list fetch)
        val recentWalk = walkRepository.findFirstByUserIdOrderByStartTimeDesc(userId)?.let { w ->
            val walkPets = w.walkPets
            val petUrls = walkPets.mapNotNull { wp ->
                wp.pet.profileImages.firstOrNull()?.imageUrl ?: wp.pet.profileImageUrl
            }
            // T1-1.5 batch prefetch
            if (petUrls.isNotEmpty()) fileAttachmentLookupService.batchLookup(petUrls)
            HomeRecentWalkResponse(
                id = w.id,
                distanceKm = w.distanceKm,
                durationSeconds = w.durationSeconds,
                endTime = w.endTime,
                startAddress = w.startAddress,
                petNames = walkPets.map { it.pet.name },
                petProfileImageUrls = petUrls,
                petProfileImageUrlsThumbnail = petUrls.map { fileAttachmentLookupService.thumbnailUrlFor(it) ?: it },
                petProfileImageUrlsViewer = petUrls.map { fileAttachmentLookupService.viewerUrlFor(it) ?: it },
                petProfileImageUrlsThumbnailWebp = petUrls.map { fileAttachmentLookupService.thumbnailUrlWebpFor(it) },
            )
        }

        // Get notification unread count
        val unreadCount = notificationService.getUnreadCount(userId)

        // Get user's pets
        val myPets = petRepository.findByOwnerId(userId)
        val myPetNames = myPets.map { it.name }

        // Today's accumulated walk stats (from midnight)
        val todayStart = LocalDate.now().atStartOfDay()
        val stats = walkRepository.aggregateWalkStats(userId, todayStart, todayStart.plusDays(1)).first()
        val earnedGold = goldTransactionRepository
            .sumAmountByUserAndTypeSince(userId, TransactionType.REWARD, todayStart) ?: 0
        val primaryPet = myPets.firstOrNull()
        val primaryPetImageUrl = primaryPet
            ?.let { it.profileImages.firstOrNull()?.imageUrl ?: it.profileImageUrl }
        val todayWalk = HomeTodayWalkResponse(
            distanceKm = (stats[0] as Number).toDouble(),
            durationMinutes = (stats[1] as Number).toLong() / 60,
            caloriesBurned = (stats[2] as Number).toDouble(),
            earnedGold = earnedGold,
            walkCount = (stats[3] as Number).toLong(),
            petName = primaryPet?.name,
            petBreed = primaryPet?.breed?.name ?: primaryPet?.species?.name,
            locationText = user.mainLocationText,
            petProfileImageUrl = primaryPetImageUrl,
            petProfileImageUrlThumbnail = primaryPetImageUrl
                ?.let { fileAttachmentLookupService.thumbnailUrlFor(it) ?: it },
            petProfileImageUrlThumbnailWebp = primaryPetImageUrl
                ?.let { fileAttachmentLookupService.thumbnailUrlWebpFor(it) },
        )

        return HomeResponse(
            recommendations = recommendations,
            recentWalk = recentWalk,
            todayWalk = todayWalk,
            notifications = NotificationSummary(
                hasUnread = unreadCount > 0,
                count = unreadCount
            ),
            myPetNames = myPetNames
        )
    }
}
