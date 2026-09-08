package com.goldpet.domain.friend.service

import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.common.util.toHttps
import com.goldpet.domain.friend.dto.FriendResponse
import com.goldpet.util.GeoUtils
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.experiment.service.ExperimentService
import com.goldpet.domain.friend.repository.FriendRepository
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.profileboost.service.ProfileBoostService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Period

data class ParsedTags(val note: String, val tags: List<String>)

fun parseTemperamentTags(raw: String?): ParsedTags {
    if (raw.isNullOrBlank()) return ParsedTags("", emptyList())
    val tagPattern = Regex("""(?:^|,)([A-Za-z]+):""")
    val matches = tagPattern.findAll(raw).toList()
    val parsed = matches.mapIndexed { i, match ->
        val start = match.range.first + (if (match.value.startsWith(",")) 1 else 0)
        val end = if (i + 1 < matches.size) matches[i + 1].range.first else raw.length
        raw.substring(start, end).trimEnd(',')
    }
    val note = parsed.find { it.startsWith("Note:") }?.substringAfter("Note:")?.trim() ?: ""
    val tags = parsed.filter { it.isNotBlank() && !it.startsWith("Note:") }
    return ParsedTags(note, tags)
}

// ── W1 Compatibility Scoring (plan §2.1) ─────────────────────────────────────
// 가중치는 SystemSetting 으로 외부화 (match.score.w_*) → 재배포 없이 튜닝.
data class CompatibilityWeights(
    val distance: Double,
    val interest: Double,
    val hobby: Double,
    val temperament: Double
)

/** |∩| / |∪| — 공집합/완전 disjoint 안전 (둘 다 비면 0). */
fun jaccard(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() && b.isEmpty()) return 0.0
    val union = a.union(b).size.toDouble()
    if (union == 0.0) return 0.0
    return a.intersect(b).size.toDouble() / union
}

/** 일치 태그 수 / max(태그 수). divide-by-zero 가드: max(tags)==0 → 0. */
fun temperamentOverlap(a: List<String>, b: List<String>): Double {
    val maxTags = maxOf(a.size, b.size)
    if (maxTags == 0) return 0.0
    val matches = a.toSet().intersect(b.toSet()).size.toDouble()
    return matches / maxTags
}

/**
 * score = w_distance*distance_norm + w_interest*interest_jaccard
 *       + w_hobby*hobby_jaccard + w_temperament*temperament_overlap
 * distance_norm = 1 - min(d, radius)/radius (radius<=0 가드 → 0).
 */
fun computeCompatibilityScore(
    distanceMeters: Double,
    radiusMeters: Double,
    myInterests: Set<String>,
    theirInterests: Set<String>,
    myHobbies: Set<String>,
    theirHobbies: Set<String>,
    myTemperament: List<String>,
    theirTemperament: List<String>,
    weights: CompatibilityWeights
): Double {
    val distanceNorm = if (radiusMeters <= 0.0) 0.0
        else 1.0 - (minOf(distanceMeters, radiusMeters) / radiusMeters)
    return weights.distance * distanceNorm +
        weights.interest * jaccard(myInterests, theirInterests) +
        weights.hobby * jaccard(myHobbies, theirHobbies) +
        weights.temperament * temperamentOverlap(myTemperament, theirTemperament)
}

/**
 * 프로필-부스트 가시성 가중(골드 sink, 플랜 §1 PARALLEL).
 * 활성 부스트 유저는 compatible 모드 재정렬에서 가산점을 받아 같은 radius 내 상위 노출.
 * bonus 는 SystemSetting profile.boost.rank_bonus, 최종 점수는 [0,1] 클램프.
 */
fun applyBoostBonus(baseScore: Double, isBoosted: Boolean, bonus: Double): Double =
    if (isBoosted) (baseScore + bonus).coerceIn(0.0, 1.0) else baseScore

@Service
@Transactional(readOnly = true)
class FriendService(
    private val friendRepository: FriendRepository,
    private val petRepository: PetRepository,
    private val fileAttachmentLookupService: FileAttachmentLookupService,
    private val userRepository: UserRepository,
    private val systemSettingService: SystemSettingService,
    private val profileBoostService: ProfileBoostService,
    private val experimentService: ExperimentService
) {
    companion object {
        // 거리 native SQL 1차 후보집합 상한 (앱-레이어 재정렬 입력). 플랜 §2.1 N=200.
        const val COMPATIBILITY_CANDIDATE_LIMIT = 200
        // '전체'(무제한) + 명시 정렬(궁합순/인기순) 시 ST_DWithin에 넘길 사실상 무제한 반경(지구 반둘레 ≈ 20,037km).
        const val UNLIMITED_RADIUS_METERS = 20_038_000.0
    }

    /**
     * PERF-003: userIds 의 pet 을 1회 IN 쿼리로 조회해 ownerId 별로 그룹핑한다.
     * 기존 per-user `petRepository.findByOwnerId(user.id)` (URL 워밍·petTypes/genders 필터·변환에서 각각
     * 재호출, 요청당 최대 ~800 쿼리)를 단일 배치로 대체. groupBy 는 쿼리 결과 순서를 보존하므로
     * `firstOrNull()`(main pet) 시맨틱은 기존 무순서 findByOwnerId 와 동일하다.
     */
    private fun petsByOwner(userIds: Collection<Long>): Map<Long, List<com.goldpet.domain.pet.entity.Pet>> {
        val distinct = userIds.distinct()
        if (distinct.isEmpty()) return emptyMap()
        return petRepository.findByOwnerIdIn(distinct).groupBy { it.owner.id }
    }

    fun getFriends(
        myUserId: Long,
        myLat: Double,
        myLng: Double,
        distanceKm: Double?,
        sortBy: String?,
        petTypes: List<String>?,
        genders: List<String>?,
        pageable: Pageable
    ): Page<FriendResponse> {
        // 친구찾기 '전체'(무제한) 경로: distance 미지정(null) + reco v2 플래그 ON.
        //  - 기본 정렬(등록순/미지정) → 홈과 동일한 findRecommendations(반경 무제한·이성 우선·신규(위치/펫 없어도) 포함·거리순).
        //  - 명시 정렬(궁합순/인기순 등) → 정렬 존중: 초대형 반경(UNLIMITED_RADIUS_METERS)으로 해당 정렬 로직을 그대로 무제한 적용.
        // 특정 거리(1/3/5km) 선택(distanceKm != null) 또는 플래그 OFF → 기존 경로 그대로(롤백 안전).
        val recoV2Unlimited = distanceKm == null &&
            systemSettingService.getBoolean("reco.recommendation_v2.enabled", false)
        if (recoV2Unlimited && (sortBy == null || sortBy == "registered")) {
            return getUnlimitedRecommendations(myUserId, myLat, myLng, petTypes, genders, pageable)
        }

        val radiusMeters = when {
            distanceKm != null -> distanceKm * 1000
            recoV2Unlimited -> UNLIMITED_RADIUS_METERS // '전체' + 궁합순/인기순 → 사실상 무제한
            else -> 5000.0 // 플래그 OFF + 거리 미지정 → 기존 5km 폴백
        }

        // W1: 신규 compatible 모드. 플래그 ON 일 때만 가중 재정렬 경로.
        // 플래그 OFF → distance-only 로 폴백(롤백 안전 — 기존 3모드 불변).
        if (sortBy == "compatible") {
            // kill-switch: match.compatibility.enabled OFF → 전원 distance (compatible 경로 자체 비활성).
            if (!systemSettingService.getBoolean("match.compatibility.enabled", false)) {
                return getFriends(myUserId, myLat, myLng, distanceKm, "distance", petTypes, genders, pageable)
            }
            // A/B(plan §1 W1(1)): experiment.compatibility.enabled ON 이면 write-once 코호트가 노출을 가른다.
            //   TREATMENT → compatible 스코어링 / CONTROL → distance. like_events 도 같은 행에서 cohort read.
            // experiment OFF → 코호트 미사용, 전원 compatible (A/B 도입 전 원래 동작 보존).
            if (systemSettingService.getBoolean("experiment.compatibility.enabled", false)) {
                val cohort = experimentService.getOrAssignCohort(myUserId, ExperimentService.COMPATIBILITY_KEY)
                return if (cohort == ExperimentService.COHORT_TREATMENT) {
                    getCompatibleFriends(myUserId, myLat, myLng, radiusMeters, petTypes, genders, pageable)
                } else {
                    getFriends(myUserId, myLat, myLng, distanceKm, "distance", petTypes, genders, pageable)
                }
            }
            return getCompatibleFriends(myUserId, myLat, myLng, radiusMeters, petTypes, genders, pageable)
        }

        // Create a new pageable WITHOUT sort - sorting is handled in native query
        val unsortedPageable = PageRequest.of(pageable.pageNumber, pageable.pageSize)

        // Use appropriate query based on sort option
        val usersPage = when (sortBy) {
            "popular" -> friendRepository.findFriendsNearbyByPopular(
                myUserId = myUserId,
                myLat = myLat,
                myLng = myLng,
                radiusMeters = radiusMeters,
                pageable = unsortedPageable
            )
            "distance" -> friendRepository.findFriendsNearbyByDistance(
                myUserId = myUserId,
                myLat = myLat,
                myLng = myLng,
                radiusMeters = radiusMeters,
                pageable = unsortedPageable
            )
            else -> friendRepository.findFriendsNearbyByRegistered(
                myUserId = myUserId,
                myLat = myLat,
                myLng = myLng,
                radiusMeters = radiusMeters,
                pageable = unsortedPageable
            )
        }

        // PERF-003: 페이지 유저의 pet 을 1회 배치 조회해 URL 워밍·필터·변환에서 재사용.
        val petsByOwner = petsByOwner(usersPage.content.map { it.id })

        // T1-1.4b batch prefetch: owner 프로필 이미지 + pet 이미지 URL 을 한 번의 IN 쿼리로 warm.
        val allUrls = usersPage.content.flatMap { user ->
            val ownerUrls = user.profileImages.map { it.imageUrl } +
                listOfNotNull(user.profileImageUrl)
            val petUrls = (petsByOwner[user.id] ?: emptyList()).flatMap { pet ->
                pet.profileImages.map { it.imageUrl } + listOfNotNull(pet.profileImageUrl)
            }
            ownerUrls + petUrls
        }
        if (allUrls.isNotEmpty()) fileAttachmentLookupService.batchLookup(allUrls)

        // Convert to FriendResponse and filter by petTypes/genders if needed
        val friendResponses = usersPage.content.mapNotNull { user ->
            val pets = petsByOwner[user.id] ?: emptyList()

            // Filter by petTypes and genders if provided
            val matchesPetType = petTypes.isNullOrEmpty() ||
                pets.any { pet -> pet.species.code in petTypes }
            val matchesGender = genders.isNullOrEmpty() ||
                pets.any { pet -> pet.gender in genders }

            if (matchesPetType && matchesGender) convertToFriendResponse(user, myLat, myLng, pets) else null
        }

        return PageImpl(friendResponses, pageable, usersPage.totalElements)
    }

    /**
     * 홈 추천 전용 경로(reco v2). 공유 `getFriends` 스위치와 분리 → `/friends`·compatibility 무영향.
     * `findRecommendations`(반경무제한·이성우선·신규완화·거리순)를 호출해 FriendResponse 로 매핑.
     * 후처리 petTypes/genders 필터 없음(홈은 필터 미사용 — 페이지네이션 불변식 보존).
     */
    fun getRecommendations(
        myUserId: Long,
        myLat: Double,
        myLng: Double,
        oppositeGender: String?,
        hasMyLocation: Boolean,
        limit: Int
    ): List<FriendResponse> {
        val users = friendRepository.findRecommendations(
            myUserId = myUserId,
            myLat = myLat,
            myLng = myLng,
            oppositeGender = oppositeGender,
            hasMyLocation = hasMyLocation,
            pageable = PageRequest.of(0, limit)
        )
        return buildFriendResponses(users, myLat, myLng)
    }

    /**
     * 친구찾기 '전체'(무제한) 경로 — 홈 reco v2 와 동일 로직을 `/friends` 페이지네이션에 얹는다.
     * findRecommendations(반경 무제한·이성 우선·신규 완화·거리순) 결과에 petTypes/genders 후처리 필터 적용.
     * sortBy 는 무시(reco 고정 정렬). 페이지네이션 last 는 필터 전 원본 크기로 판단(가득 차면 다음 페이지 有).
     */
    private fun getUnlimitedRecommendations(
        myUserId: Long,
        myLat: Double,
        myLng: Double,
        petTypes: List<String>?,
        genders: List<String>?,
        pageable: Pageable
    ): Page<FriendResponse> {
        val me = userRepository.findById(myUserId).orElse(null)
        val oppositeGender = when (me?.gender) {
            "MALE" -> "FEMALE"
            "FEMALE" -> "MALE"
            else -> null
        }
        val hasMyLocation = me?.mainLocationGeom != null

        // native @Query 에는 Sort 없는 Pageable 을 넘긴다. 컨트롤러가 받은 pageable 에는
        // 요청 파라미터 ?sort=registered 가 Spring 에 의해 Sort(registered) 로 해석돼 들어있는데,
        // 이를 native 쿼리에 넘기면 Hibernate 가 `ORDER BY u.registered`(존재하지 않는 컬럼)를 덧붙여
        // SQL 문법 오류(42601)가 난다. 기존 정렬 경로도 동일 이유로 sort 를 벗긴 pageable 을 쓴다.
        val unsortedPageable = PageRequest.of(pageable.pageNumber, pageable.pageSize)
        val users = friendRepository.findRecommendations(
            myUserId = myUserId,
            myLat = myLat,
            myLng = myLng,
            oppositeGender = oppositeGender,
            hasMyLocation = hasMyLocation,
            pageable = unsortedPageable
        )

        // 다음 페이지 유무는 필터 전 원본 크기 기준(LIMIT 가득 차면 다음 페이지 있음).
        // 후처리 필터로 content 가 줄어도 페이지네이션은 멈추지 않는다.
        val hasNext = users.size >= pageable.pageSize

        // petTypes/genders 후처리 필터 — 기존 getFriends 필터 로직과 동일.
        // PERF-003: 후보 pet 을 1회 배치 조회해 per-user findByOwnerId N+1 제거.
        val petsByOwner = petsByOwner(users.map { it.id })
        val filtered = users.filter { user ->
            val pets = petsByOwner[user.id] ?: emptyList()
            val matchesPetType = petTypes.isNullOrEmpty() ||
                pets.any { pet -> pet.species.code in petTypes }
            val matchesGender = genders.isNullOrEmpty() ||
                pets.any { pet -> pet.gender in genders }
            matchesPetType && matchesGender
        }

        val responses = buildFriendResponses(filtered, myLat, myLng)

        // PageImpl.isLast() 가 hasNext 를 반영하도록 total 을 역산:
        //   다음 페이지 有 → total 을 현재 페이지 끝 너머로 밀어 isLast=false,
        //   없음 → 현재 페이지까지만 → isLast=true.
        val total = if (hasNext) {
            (pageable.pageNumber.toLong() + 1) * pageable.pageSize + 1
        } else {
            pageable.offset + responses.size
        }
        return PageImpl(responses, pageable, total)
    }

    /**
     * W1 Compatibility Scoring (플랜 §2.1).
     * 거리 native SQL 로 radius 내 상위 N=200 후보집합 1차 조회 → 앱-레이어 가중 재정렬.
     * 페이지네이션은 스코어 정렬된 in-memory 집합에 적용(거리 페이징 충돌 회피).
     */
    private fun getCompatibleFriends(
        myUserId: Long,
        myLat: Double,
        myLng: Double,
        radiusMeters: Double,
        petTypes: List<String>?,
        genders: List<String>?,
        pageable: Pageable
    ): Page<FriendResponse> {
        // 1차 후보집합: 거리순 상위 N (기존 distance 모드 native SQL 재사용, 불변).
        val candidatePageable = PageRequest.of(0, COMPATIBILITY_CANDIDATE_LIMIT)
        val candidateUsers = friendRepository.findFriendsNearbyByDistance(
            myUserId = myUserId,
            myLat = myLat,
            myLng = myLng,
            radiusMeters = radiusMeters,
            pageable = candidatePageable
        ).content

        // PERF-003: 후보 + 나 자신의 pet 을 1회 배치 조회해 필터·스코어링·temperament 에서 재사용.
        val petsByOwner = petsByOwner(candidateUsers.map { it.id } + myUserId)

        val candidates = candidateUsers.filter { user ->
            val pets = petsByOwner[user.id] ?: emptyList()
            val matchesPetType = petTypes.isNullOrEmpty() ||
                pets.any { pet -> pet.species.code in petTypes }
            val matchesGender = genders.isNullOrEmpty() ||
                pets.any { pet -> pet.gender in genders }
            matchesPetType && matchesGender
        }

        // 내 프로필 신호: interests/hobbies(User) + temperament(main Pet, CSV plumbing).
        val me = userRepository.findById(myUserId).orElse(null)
        val myInterests = me?.interests?.map { it.name }?.toSet() ?: emptySet()
        val myHobbies = me?.hobbies?.map { it.name }?.toSet() ?: emptySet()
        val myTemperament = parseTemperamentTags(
            (petsByOwner[myUserId] ?: emptyList()).firstOrNull()?.temperamentTags
        ).tags

        val weights = loadCompatibilityWeights()

        // 프로필-부스트 가시성 가중(골드 sink): 활성 부스트 유저 집합 + 가산점(클램프).
        val boostedIds = profileBoostService.boostedUserIds()
        val boostBonus = systemSettingService.getString("profile.boost.rank_bonus", "0.15")
            .toDoubleOrNull() ?: 0.15

        val scored = candidates.map { user ->
            val theirInterests = user.interests.map { it.name }.toSet()
            val theirHobbies = user.hobbies.map { it.name }.toSet()
            val theirTemperament = parseTemperamentTags(
                (petsByOwner[user.id] ?: emptyList()).firstOrNull()?.temperamentTags
            ).tags
            val distance = GeoUtils.calculateDistanceInMeters(
                myLat, myLng, user.mainLocationGeom?.y, user.mainLocationGeom?.x
            )
            val baseScore = computeCompatibilityScore(
                distanceMeters = distance,
                radiusMeters = radiusMeters,
                myInterests = myInterests,
                theirInterests = theirInterests,
                myHobbies = myHobbies,
                theirHobbies = theirHobbies,
                myTemperament = myTemperament,
                theirTemperament = theirTemperament,
                weights = weights
            )
            val score = applyBoostBonus(baseScore, user.id in boostedIds, boostBonus)
            user to score
        }.sortedByDescending { it.second }

        // 스코어 정렬된 in-memory 집합에 페이지네이션 적용.
        val total = scored.size.toLong()
        val from = (pageable.pageNumber * pageable.pageSize).coerceIn(0, scored.size)
        val to = (from + pageable.pageSize).coerceIn(0, scored.size)
        val pageUsers = scored.subList(from, to).map { it.first }

        val responses = buildFriendResponses(pageUsers, myLat, myLng)
        return PageImpl(responses, pageable, total)
    }

    private fun loadCompatibilityWeights(): CompatibilityWeights = CompatibilityWeights(
        distance = systemSettingService.getString("match.score.w_distance", "0.40").toDoubleOrNull() ?: 0.40,
        interest = systemSettingService.getString("match.score.w_interest", "0.25").toDoubleOrNull() ?: 0.25,
        hobby = systemSettingService.getString("match.score.w_hobby", "0.20").toDoubleOrNull() ?: 0.20,
        temperament = systemSettingService.getString("match.score.w_temperament", "0.15").toDoubleOrNull() ?: 0.15
    )

    /**
     * T1-1.4b 연장 — LikeController 3 endpoint 공용 매퍼.
     * User 리스트를 받아 batch prefetch(variant lookup N+1 해소) + 개별 변환을 수행한다.
     * FriendController 의 리스트 경로와 동일한 variant 공급 정책을 보장.
     */
    fun buildFriendResponses(users: List<User>, myLat: Double, myLng: Double): List<FriendResponse> {
        if (users.isEmpty()) return emptyList()

        // PERF-003: pet 을 1회 배치 조회해 URL 워밍·변환에서 재사용 (per-user findByOwnerId 제거).
        val petsByOwner = petsByOwner(users.map { it.id })

        val allUrls = users.flatMap { user ->
            val ownerUrls = user.profileImages.map { it.imageUrl } +
                listOfNotNull(user.profileImageUrl)
            val petUrls = (petsByOwner[user.id] ?: emptyList()).flatMap { pet ->
                pet.profileImages.map { it.imageUrl } + listOfNotNull(pet.profileImageUrl)
            }
            ownerUrls + petUrls
        }
        if (allUrls.isNotEmpty()) fileAttachmentLookupService.batchLookup(allUrls)

        return users.map { convertToFriendResponse(it, myLat, myLng, petsByOwner[it.id] ?: emptyList()) }
    }

    private fun convertToFriendResponse(
        user: User,
        myLat: Double,
        myLng: Double,
        pets: List<com.goldpet.domain.pet.entity.Pet>
    ): FriendResponse {
        val mainPet = pets.firstOrNull()
        
        // Calculate age
        val petAge = mainPet?.birthDate?.let { Period.between(it, LocalDate.now()).years } ?: 0

        val distance = GeoUtils.calculateDistanceInMeters(myLat, myLng, user.mainLocationGeom?.y, user.mainLocationGeom?.x)

        // Get pet images from ALL pets: profileImages relation first, fallback to profileImageUrl
        val petImages = pets.flatMap { pet ->
            val images = pet.profileImages.map { it.imageUrl }
            if (images.isEmpty() && !pet.profileImageUrl.isNullOrBlank()) {
                listOf(pet.profileImageUrl!!)
            } else {
                images
            }
        }

        // Get owner profile images: from profileImages relation, fallback to profileImageUrl
        val ownerImages = user.profileImages.map { it.imageUrl }.toMutableList()
        if (ownerImages.isEmpty() && !user.profileImageUrl.isNullOrBlank()) {
            ownerImages.add(user.profileImageUrl!!)
        }

        val parsed = parseTemperamentTags(mainPet?.temperamentTags)

        val ownerAge = user.birthDate?.let { Period.between(it, LocalDate.now()).years }

        val ownerPrimary = user.profileImageUrl.toHttps()
        return FriendResponse(
            id = user.id,
            nickname = user.nickname ?: "Unknown",
            profileImageUrl = ownerPrimary, // Owner's main profile image (small, secondary)
            profileImageUrlThumbnail = fileAttachmentLookupService.thumbnailUrlFor(ownerPrimary),
            profileImageUrlViewer = fileAttachmentLookupService.viewerUrlFor(ownerPrimary),
            profileImages = ownerImages, // Owner's all profile images
            profileImagesThumbnail = ownerImages.map { fileAttachmentLookupService.thumbnailUrlFor(it) ?: it },
            profileImagesViewer = ownerImages.map { fileAttachmentLookupService.viewerUrlFor(it) ?: it },
            profileImageUrlThumbnailWebp = fileAttachmentLookupService.thumbnailUrlWebpFor(ownerPrimary),
            profileImagesThumbnailWebp = ownerImages.map { fileAttachmentLookupService.thumbnailUrlWebpFor(it) },
            ownerGender = user.gender,
            ownerAge = ownerAge,
            ownerIntro = user.intro,
            ownerMbti = user.mbti,
            ownerInterests = user.interests.map { it.name },
            ownerHobbies = user.hobbies.map { it.name },
            // 펫 미등록 회원(reco v2 신규 완화로 노출됨): 이름=회원 닉네임, 품종="예비 집사 🐾"
            petName = mainPet?.name ?: user.nickname ?: "예비 집사",
            petBreed = if (mainPet == null) "예비 집사 🐾" else (mainPet.breed?.name ?: mainPet.species?.name ?: "반려동물"),
            petAge = petAge,
            petGender = mainPet?.gender ?: "UNKNOWN",
            isNeutered = mainPet?.isNeutered ?: false,
            onWalking = false,
            description = parsed.note,
            tags = parsed.tags,
            images = petImages, // Pet images (large, main slider)
            imagesThumbnail = petImages.map { fileAttachmentLookupService.thumbnailUrlFor(it) ?: it },
            imagesViewer = petImages.map { fileAttachmentLookupService.viewerUrlFor(it) ?: it },
            imagesThumbnailWebp = petImages.map { fileAttachmentLookupService.thumbnailUrlWebpFor(it) },
            distance = distance,
            status = "offline",
            location = null, // Privacy: precise coordinates not exposed
            locationText = user.mainLocationText
        )
    }

}
