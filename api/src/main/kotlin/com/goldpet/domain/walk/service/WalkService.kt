package com.goldpet.domain.walk.service

import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.gamification.service.BadgeAwardService
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.walk.dto.CreateWalkRequest
import com.goldpet.domain.walk.dto.PhotoMintResponse
import com.goldpet.domain.walk.dto.UserWalkPhotosPage
import com.goldpet.domain.walk.dto.WalkPhotoResponse
import com.goldpet.domain.walk.dto.WalkResponse
import com.goldpet.domain.walk.dto.BoundingBoxRequest
import com.goldpet.domain.common.util.toHttps
import com.goldpet.domain.walk.dto.WalkRankingResponse
import com.goldpet.domain.walk.dto.WalkCoupleRankingResponse
import com.goldpet.domain.walk.entity.Walk
import com.goldpet.domain.walk.entity.WalkPet
import com.goldpet.domain.walk.repository.WalkRepository
import com.goldpet.domain.walk.repository.WalkSpotRepository
import com.goldpet.domain.walk.repository.BestWalkCoupleRepository
import com.goldpet.domain.common.repository.FileAttachmentRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import com.goldpet.domain.walk.event.WalkCompletedEvent
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.infra.GeocodingService
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.Executor

@Service
@Transactional(readOnly = true)
class WalkService(
    private val walkRepository: WalkRepository,
    private val walkSpotRepository: WalkSpotRepository,
    private val bestWalkCoupleRepository: BestWalkCoupleRepository,
    private val userRepository: UserRepository,
    private val petRepository: PetRepository,
    private val goldService: GoldService,
    private val badgeAwardService: BadgeAwardService,
    private val geocodingService: GeocodingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val systemSettingService: SystemSettingService,
    private val userBlockRepository: UserBlockRepository,
    private val photoUrlSigner: PhotoUrlSigner,
    private val meterRegistry: MeterRegistry,
    private val fileAttachmentRepository: FileAttachmentRepository,
    // PERF-007 (적대적 리뷰 MEDIUM) — 종료점 지오코딩 async 를 JVM 공용 commonPool 대신 전용 bounded
    // 풀에서 실행(AsyncConfig.geocodingExecutor). commonPool 기아 방지.
    @Qualifier("geocodingExecutor") private val geocodingExecutor: Executor
) {
    private val log = LoggerFactory.getLogger(WalkService::class.java)

    companion object {
        // W-1 (EXT-CDX-008) — durationSeconds 가 wall-clock(endTime-startTime)을 초과할 때
        // 허용할 반올림/초단위 truncation 오차. 정책적 유예가 아니라 계산 오차 보정이므로 작게 유지.
        private const val DEFAULT_WALLCLOCK_TOLERANCE_SECONDS = 10
        // W-1 — 1회 산책 현실적 상한(런타임 설정 가능, walk.max.speed.kmh 패턴 재사용).
        private const val DEFAULT_MAX_DISTANCE_KM = 50
        private const val DEFAULT_MAX_DURATION_SECONDS = 21_600 // 6h
    }

    // Counters for presigned URL emission (feed path)
    private val presignedServedCounter: Counter by lazy {
        Counter.builder("walk_photo_presigned_served_total")
            .tag("bucket", "private")
            .description("Presigned URLs emitted in walk photo feed responses")
            .register(meterRegistry)
    }
    private val hiddenPresignedServedCounter: Counter by lazy {
        Counter.builder("walk_photo_hidden_presigned_served_total")
            .description("Presigned URLs emitted for owner-visible hidden walk photos")
            .register(meterRegistry)
    }

    // Counters for per-spot mint endpoint (§3.7)
    private val mintOkCounter: Counter by lazy {
        Counter.builder("walk_photo_mint_endpoint_total")
            .tag("result", "ok")
            .description("Successful per-spot URL mint requests")
            .register(meterRegistry)
    }
    private val mintDeniedCounter: Counter by lazy {
        Counter.builder("walk_photo_mint_endpoint_total")
            .tag("result", "denied")
            .description("Per-spot URL mint requests denied (403)")
            .register(meterRegistry)
    }
    private val mintNotFoundCounter: Counter by lazy {
        Counter.builder("walk_photo_mint_endpoint_total")
            .tag("result", "not_found")
            .description("Per-spot URL mint requests for unknown spots (404)")
            .register(meterRegistry)
    }

    @Transactional
    fun createWalk(userId: Long, request: CreateWalkRequest): WalkResponse {
        // EXT-CDX-008: 거리는 클라이언트 값(request.distanceKm)을 신뢰하지 않고 저장된 path(GPS 좌표열)
        // 로 서버가 재계산한다. 보상·랭킹의 단일 진실은 서버 산출 거리.
        val serverDistanceKm = haversineDistanceKm(request.path)

        // W-1 (EXT-CDX-008 적대적 리뷰) — durationSeconds(클라 값)가 startTime~endTime 실제 경과
        // (wall-clock)를 초과하면 물리적으로 불가능(활동시간 > 총경과시간)하므로 거부한다.
        // 근거: 클라이언트(app/lib/.../walk_tracking_mixin.dart:676-677,847-848)의 실제 계산식은
        //   durationSeconds = (endTime - startTime - totalPauseDuration).inSeconds
        // pauseDuration >= 0 이므로 정상 클라이언트는 durationSeconds <= wallClockSeconds 를 항상
        // 만족한다. 즉 "멈춤 구간"은 durationSeconds 를 wall-clock보다 작게 만드는 방향으로만
        // 작용 — 그 방향은 절대 거부하지 않는다(장시간+멈춤 포함 산책 회귀 없음). 초과하는
        // 경우만 durationSeconds 를 인위적으로 부풀려 평균속도(아래 속도상한 검증)를 낮춰
        // 보이게 하는 조작으로 간주해 차단한다. 상수 여유(rounding-safety)는 초 단위 truncation
        // 차이만 흡수하도록 작게 유지 — 정책적 유예가 아니라 반올림 오차 보정이다.
        val wallClockSeconds = java.time.Duration.between(request.startTime, request.endTime).seconds
        val toleranceSeconds = systemSettingService.getInt(
            "walk.duration.wallclock.tolerance.seconds", DEFAULT_WALLCLOCK_TOLERANCE_SECONDS
        )
        if (request.durationSeconds > wallClockSeconds + toleranceSeconds) {
            throw BadRequestException(
                "durationSeconds(${request.durationSeconds}초)가 산책 시작~종료 경과시간" +
                "(${wallClockSeconds}초)을 초과할 수 없습니다."
            )
        }
        // 속도 계산은 wall-clock 을 넘지 않도록 clamp 된 duration 사용(위 검증의 허용오차
        // 구간에서도 속도 계산이 유리하게 부풀지 않도록).
        val validatedDurationSeconds =
            if (wallClockSeconds > 0) minOf(request.durationSeconds, wallClockSeconds) else request.durationSeconds

        // W-1 — 1회 산책의 현실적 상한(거리/시간). 매우 큰 값으로 보상/랭킹을 위조하는 것을
        // wall-clock 정합성과 무관하게(둘 다 일관되게 부풀린 경우까지) 차단하는 방어선.
        val maxDistanceKm = systemSettingService.getInt("walk.max.distance.km", DEFAULT_MAX_DISTANCE_KM)
        val maxDurationSeconds = systemSettingService.getInt("walk.max.duration.seconds", DEFAULT_MAX_DURATION_SECONDS)
        if (serverDistanceKm > maxDistanceKm) {
            throw BadRequestException(
                "1회 산책 거리 상한(${maxDistanceKm}km)을 초과했습니다 " +
                "(서버 산출: ${String.format("%.1f", serverDistanceKm)}km)"
            )
        }
        if (request.durationSeconds > maxDurationSeconds) {
            throw BadRequestException("1회 산책 시간 상한(${maxDurationSeconds}초)을 초과했습니다.")
        }

        // 산책 최소 저장 조건 검증 (서버 산출 거리 기준)
        val minDuration = systemSettingService.getInt("WALK_MIN_DURATION_SECONDS", 30)
        val minDistance = systemSettingService.getInt("WALK_MIN_DISTANCE_METERS", 10)
        val distanceMeters = (serverDistanceKm * 1000).toInt()
        if (request.durationSeconds < minDuration || distanceMeters < minDistance) {
            throw BadRequestException(
                "산책 저장 조건 미충족: ${minDuration}초 이상, ${minDistance}m 이상 필요 " +
                "(현재: ${request.durationSeconds}초, ${distanceMeters}m)"
            )
        }

        // 비현실적 이동 속도 거부: 서버 거리 / 교차검증된(wall-clock 초과 불가) 시간으로 평균
        // 속도를 구해 상한 초과 시 조작으로 간주.
        // (path 조작으로 짧은 시간에 먼 거리를 위조하는 보상/랭킹 어뷰징 차단.)
        val maxSpeedKmh = systemSettingService.getInt("walk.max.speed.kmh", 30)
        val hours = validatedDurationSeconds / 3600.0
        if (hours > 0 && serverDistanceKm / hours > maxSpeedKmh) {
            throw BadRequestException(
                "비정상적인 이동 속도가 감지되었습니다 " +
                "(${String.format("%.1f", serverDistanceKm / hours)}km/h, 상한 ${maxSpeedKmh}km/h)"
            )
        }

        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }

        val walk = Walk(
            user = user,
            startTime = request.startTime,
            endTime = request.endTime,
            distanceKm = serverDistanceKm,
            durationSeconds = request.durationSeconds,
            path = request.toLineString(),
            caloriesBurned = request.caloriesBurned,
            notes = request.notes,
            isPublic = request.isPublic
        )
        walk.followedCourseId = request.followedCourseId
        
        // Add spots
        val geometryFactory = GeometryFactory(PrecisionModel(), 4326)
        // Batch-fetch attachments once to avoid N+1 findByUrl inside the loop.
        // Variant keys may be null if async variant-gen is still in flight;
        // WalkSpotVariantSyncScheduler reconciles these on its 5-minute tick.
        val spotUrls = request.spots.mapNotNull { it.imageUrl }.distinct()
        val attachmentsByUrl: Map<String, com.goldpet.domain.common.entity.FileAttachment> =
            if (spotUrls.isEmpty()) emptyMap()
            else try {
                fileAttachmentRepository.findAllByUrlIn(spotUrls).associateBy { it.url }
            } catch (e: Exception) {
                log.warn("findAllByUrlIn failed for {} urls: {}", spotUrls.size, e.message)
                emptyMap()
            }
        request.spots.forEach { spotDto ->
            val point = geometryFactory.createPoint(Coordinate(spotDto.longitude, spotDto.latitude))
            val attachment = spotDto.imageUrl?.let { attachmentsByUrl[it] }
            walk.spots.add(com.goldpet.domain.walk.entity.WalkSpot(
                walk = walk,
                location = point,
                type = spotDto.type,
                timestamp = spotDto.timestamp,
                imageUrl = spotDto.imageUrl,
                note = spotDto.note,
                imageKeyViewer = attachment?.viewerUrl,
                // 변형 생성이 실패한 첨부는 mediumUrl 이 원본 키로 대체(degraded)돼 있으므로, 실제 600px
                // `_medium` 변형일 때만 채운다. 아니면 null → 백필 워커가 생성하며 채운다.
                imageKeyMedium = attachment?.mediumUrl?.takeIf { it.contains("_medium") },
                imageKeyThumb = attachment?.thumbnailUrl
            ))
        }

        // Derive start/end locations from path (server-side, not from client)
        val startCoord = request.path.firstOrNull()
        val endCoord = request.path.lastOrNull()

        // PERF-007: 시작/종료 리버스 지오코딩(각 blocking HTTP, connect/read 3초)을 순차 대신 병렬 실행해
        // 쓰기 트랜잭션의 커넥션 점유 시간을 최악 ~6초 → ~3초로 단축. 종료점 호출을 async 로 띄우고
        // 시작점 호출을 현재 스레드에서 수행해 겹친다. 각 호출은 기존과 동일하게 non-fatal(실패=주소 null 유지).
        // 지오코딩은 순수 HTTP(무 DB)라 async 스레드에 트랜잭션 전파가 필요 없다.
        // 적대적 리뷰 MEDIUM 후속: executor 미지정 시 JVM 공용 commonPool 을 타 블로킹 I/O 로 점유해
        // parallel stream 등 전역에 영향을 줄 수 있어 전용 `geocodingExecutor`(AsyncConfig)를 명시.
        val endAddressFuture: java.util.concurrent.CompletableFuture<String?>? = endCoord?.let { c ->
            java.util.concurrent.CompletableFuture.supplyAsync({
                try {
                    geocodingService.reverseGeocode(c[0], c[1])
                } catch (e: Exception) {
                    log.warn("Reverse geocoding failed for walk end: {}", e.message)
                    null
                }
            }, geocodingExecutor)
        }
        if (startCoord != null) {
            walk.startLocation = geometryFactory.createPoint(Coordinate(startCoord[1], startCoord[0])) // [lon, lat]
            try {
                val geocodeResult = geocodingService.reverseGeocodeStructured(startCoord[0], startCoord[1])
                walk.startAddress = geocodeResult.fullAddress
                walk.province = geocodeResult.province
            } catch (e: Exception) {
                // Geocoding failure is non-fatal — walk save continues
                log.warn("Reverse geocoding failed for walk start: {}", e.message)
            }
        }
        if (endCoord != null) {
            walk.endLocation = geometryFactory.createPoint(Coordinate(endCoord[1], endCoord[0])) // [lon, lat]
            walk.endAddress = endAddressFuture?.join()
        }

        // Resolve pet ownership and create WalkPet associations
        if (request.petIds.isNotEmpty()) {
            val ownedPets = petRepository.findByOwnerId(userId).filter { it.id in request.petIds }
            walk.walkPets = ownedPets.map { WalkPet(walk = walk, pet = it) }.toMutableList()
        }

        val savedWalk = walkRepository.save(walk)

        // 산책 골드 보상 (거리 기반) — walk save와 원자적: 골드 실패 시 walk도 롤백
        val goldReward = calculateWalkReward(savedWalk.distanceKm)
        if (goldReward > 0) {
            goldService.grantReward(userId, goldReward, "산책 완료 보상 (${String.format("%.1f", savedWalk.distanceKm)}km)")
        }

        // Evaluate badge award conditions
        try {
            badgeAwardService.checkAndAwardBadges(userId, BadgeConditionType.WALK_COUNT)
            badgeAwardService.checkAndAwardBadges(userId, BadgeConditionType.WALK_DISTANCE_TOTAL)
        } catch (e: Exception) {
            log.warn("Failed to update gamification progress", e)
        }

        applicationEventPublisher.publishEvent(
            WalkCompletedEvent(this, savedWalk.id, userId, savedWalk.followedCourseId, savedWalk.distanceKm, goldReward)
        )

        return WalkResponse.from(savedWalk, photoUrlSigner)
    }

    fun getWalk(walkId: Long, currentUserId: Long?): WalkResponse {
        val walk = walkRepository.findById(walkId).orElseThrow { NotFoundException("Walk not found") }
        val isOwner = currentUserId != null && walk.user.id == currentUserId
        if (isOwner) {
            return WalkResponse.from(walk, photoUrlSigner)
        }
        if (!walk.isPublic) {
            throw ForbiddenException("This walk is private")
        }
        return WalkResponse.publicFrom(walk, photoUrlSigner)
    }

    fun getPublicWalks(pageable: org.springframework.data.domain.Pageable, yearMonth: String? = null, userId: Long? = null, province: String? = null): org.springframework.data.domain.Page<WalkResponse> {
        val walks = if (userId != null && yearMonth != null) {
            val (start, end) = parseYearMonth(yearMonth)
            walkRepository.findPublicWalksByUserIdAndMonth(userId, start, end, province, pageable)
        } else if (userId != null) {
            walkRepository.findPublicWalksByUserId(userId, province, pageable)
        } else if (yearMonth != null) {
            val (start, end) = parseYearMonth(yearMonth)
            walkRepository.findPublicWalksByMonth(start, end, province, pageable)
        } else {
            walkRepository.findPublicWalks(province, pageable)
        }
        return walks.map { WalkResponse.publicFrom(it, photoUrlSigner) }
    }

    fun getWalksByUser(userId: Long): List<WalkResponse> {
        val walks = walkRepository.findByUserIdOrderByStartTimeDesc(userId)
        return walks.map { WalkResponse.from(it, photoUrlSigner) }
    }

    fun getWalksByUser(userId: Long, pageable: org.springframework.data.domain.Pageable, yearMonth: String? = null): org.springframework.data.domain.Page<WalkResponse> {
        val walks = if (yearMonth != null) {
            val (start, end) = parseYearMonth(yearMonth)
            walkRepository.findByUserIdAndMonth(userId, start, end, pageable)
        } else {
            walkRepository.findByUserIdOrderByStartTimeDesc(userId, pageable)
        }
        return walks.map { WalkResponse.from(it, photoUrlSigner) }
    }

    fun findWalksInArea(request: BoundingBoxRequest): List<WalkResponse> {
        // PERF-011: bbox 검색 결과 상한(runtime 설정). 밀집지역 줌아웃 시 대량 반환 + N+1 재노출 방어.
        val maxResults = systemSettingService.getInt("walk.search.bbox.max_results", 500)
        val walks = walkRepository.findWalksWithinBoundingBox(
            request.minLat,
            request.minLon,
            request.maxLat,
            request.maxLon,
            maxResults
        )
        return walks.map { WalkResponse.from(it, photoUrlSigner) }
    }

    fun getWalkRanking(period: String, pageable: org.springframework.data.domain.Pageable, province: String? = null): List<WalkRankingResponse> {
        val since = when (period) {
            "monthly" -> java.time.LocalDateTime.now().minusDays(30)
            else -> java.time.LocalDateTime.now().minusDays(7) // weekly default
        }

        val results = walkRepository.findRankingsSince(since, province, pageable)
        return results.mapIndexed { index, row ->
            WalkRankingResponse(
                rank = index + 1,
                userId = (row[0] as Number).toLong(),
                nickname = row[1] as String,
                profileImageUrl = (row[2] as? String).toHttps(),
                petName = null,
                petProfileImageUrl = null,
                totalDistanceKm = (row[3] as Number).toDouble(),
                totalMinutes = 0L,
                totalGold = 0,
                walkCount = (row[4] as Number).toInt()
            )
        }
    }

    fun getCalendarRanking(yearMonth: String, pageable: org.springframework.data.domain.Pageable, province: String? = null): WalkCoupleRankingResponse {
        val (year, month) = yearMonth.split("-").map { it.toInt() }
        val startOfMonth = java.time.LocalDateTime.of(year, month, 1, 0, 0, 0)
        val startOfNextMonth = startOfMonth.plusMonths(1)

        val results = walkRepository.findCalendarRankings(startOfMonth, startOfNextMonth, province, pageable)
        val rankings = results.mapIndexed { index, row ->
            WalkRankingResponse(
                rank = index + 1,
                userId = (row[0] as Number).toLong(),
                nickname = row[1] as String,
                profileImageUrl = (row[2] as? String).toHttps(),
                // row[3] is petId (skip for public ranking response)
                petName = row[4] as? String,
                petProfileImageUrl = (row[5] as? String).toHttps(),
                totalDistanceKm = (row[6] as Number).toDouble(),
                totalMinutes = (row[7] as Number).toLong(),
                totalGold = (row[8] as Number).toInt(),
                walkCount = (row[9] as Number).toInt()
            )
        }

        val bestCoupleEntity = bestWalkCoupleRepository.findByYearMonth(yearMonth)
        val bestCouple = bestCoupleEntity?.let { couple ->
            rankings.find { it.userId == couple.user.id }
                ?: walkRepository.findCalendarRankingForUser(startOfMonth, startOfNextMonth, couple.user.id, province)?.takeIf { it.isNotEmpty() }?.let { row ->
                    WalkRankingResponse(
                        rank = 0,
                        userId = (row[0] as Number).toLong(),
                        nickname = row[1] as String,
                        profileImageUrl = (row[2] as? String).toHttps(),
                        petName = row[4] as? String,
                        petProfileImageUrl = (row[5] as? String).toHttps(),
                        totalDistanceKm = (row[6] as Number).toDouble(),
                        totalMinutes = (row[7] as Number).toLong(),
                        totalGold = (row[8] as Number).toInt(),
                        walkCount = (row[9] as Number).toInt()
                    )
                }
        }

        return WalkCoupleRankingResponse(
            yearMonth = yearMonth,
            bestCouple = bestCouple,
            rankings = rankings
        )
    }

    fun getMyStats(userId: Long): com.goldpet.domain.walk.dto.WalkStatsResponse {
        val totalDistance = walkRepository.sumDistanceByUserId(userId)
        val totalDuration = walkRepository.sumDurationByUserId(userId)
        val totalCalories = walkRepository.sumCaloriesByUserId(userId)
        val totalWalks = walkRepository.countByUserId(userId)
        val totalGold = goldService.getTotalRewardAmount(userId)

        return com.goldpet.domain.walk.dto.WalkStatsResponse(
            totalDistanceKm = totalDistance,
            totalDurationMinutes = totalDuration / 60,
            totalCalories = totalCalories,
            totalGoldEarned = totalGold,
            totalWalks = totalWalks
        )
    }

    fun getMyPhotos(userId: Long, yearMonth: String?, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<WalkPhotoResponse> {
        val spots = if (yearMonth != null) {
            val (start, end) = parseYearMonth(yearMonth)
            walkSpotRepository.findPhotoSpotsByUserIdAndMonth(userId, start, end, pageable)
        } else {
            walkSpotRepository.findPhotoSpotsByUserId(userId, pageable)
        }
        return spots.map { spot -> toWalkPhotoResponse(spot) }
    }

    @Transactional
    fun updateSpotNote(userId: Long, walkId: Long, spotId: Long, note: String?): WalkPhotoResponse {
        val walk = walkRepository.findById(walkId).orElseThrow { NotFoundException("Walk not found") }
        if (walk.user.id != userId) throw ForbiddenException("Not the owner of this walk")
        val spot = walkSpotRepository.findById(spotId).orElseThrow { NotFoundException("Spot not found") }
        if (spot.walk.id != walkId) throw ForbiddenException("Spot does not belong to this walk")
        spot.note = note
        return toWalkPhotoResponse(spot)
    }

    fun getPublicPhotos(userId: Long, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<WalkPhotoResponse> {
        val blockedUserIds = userBlockRepository.findBlockedUserIds(userId)
        val effectiveBlockedIds = if (blockedUserIds.isEmpty()) listOf(-1L) else blockedUserIds
        return walkSpotRepository.findPublicPhotoSpots(effectiveBlockedIds, pageable)
            .map { spot -> toWalkPhotoResponse(spot, suppressUrl = spot.hiddenFromPublic) }
    }

    // §3.7 Cold-path per-spot URL mint endpoint backing.
    // Re-derives the same authorization predicate as WalkSpotRepository.findPublicPhotoSpots
    // (public walk + not hidden + owner not blocked), plus an always-allowed owner path.
    // TODO(rate-limit): wire a Redis-backed per-user token bucket (~30 req/min) once abstraction exists.
    fun mintPhotoUrl(spotId: Long, userId: Long): PhotoMintResponse {
        val spot = walkSpotRepository.findById(spotId).orElse(null)
        if (spot == null) {
            mintNotFoundCounter.increment()
            throw NotFoundException("Spot not found: $spotId")
        }

        val isOwner = spot.walk.user.id == userId
        if (!isOwner) {
            if (spot.hiddenFromPublic || !spot.walk.isPublic) {
                mintDeniedCounter.increment()
                throw ForbiddenException("Not authorized to view this photo")
            }
            val blockedUserIds = userBlockRepository.findBlockedUserIds(userId)
            if (spot.walk.user.id in blockedUserIds) {
                mintDeniedCounter.increment()
                throw ForbiddenException("Not authorized to view this photo")
            }
        }

        val rawKey = spot.imageUrl
        val signed = photoUrlSigner.signedUrlOrNull(rawKey)
        mintOkCounter.increment()
        // T1-4: proxy endpoint deprecated. signing 실패 시 rawKey fallback 금지 (null 반환)
        //   → 클라이언트는 DOM 에서 조용히 skip, 더 이상 /files/{key}/content 호출 유도하지 않음.
        return PhotoMintResponse(
            spotId = spot.id,
            imageUrl = signed,
            imageKey = rawKey,
            imageUrlViewer = photoUrlSigner.signedViewerUrlOrNull(spot.imageKeyViewer),
            imageUrlMedium = photoUrlSigner.signedMediumUrlOrNull(spot.imageKeyMedium),
            imageUrlThumb = photoUrlSigner.signedThumbUrlOrNull(spot.imageKeyThumb),
            imageKeyViewer = spot.imageKeyViewer,
            imageKeyThumb = spot.imageKeyThumb,
            expiresAt = java.time.LocalDateTime.now().plusMinutes(PhotoUrlSigner.PRESIGN_TTL_MINUTES)
        )
    }

    private fun toWalkPhotoResponse(
        spot: com.goldpet.domain.walk.entity.WalkSpot,
        suppressUrl: Boolean = false
    ): WalkPhotoResponse {
        val firstPet = spot.walk.walkPets.firstOrNull()?.pet
        val rawKey = spot.imageUrl
        val signed = if (suppressUrl) null else photoUrlSigner.signedUrlOrNull(rawKey)
        // T1-4: signing 실패 시 rawKey fallback 금지. DOM 에서 조용히 skip.
        val resolvedUrl = signed
        if (signed != null) {
            presignedServedCounter.increment()
            if (spot.hiddenFromPublic) hiddenPresignedServedCounter.increment()
        }
        val viewerUrl = if (suppressUrl) null else photoUrlSigner.signedViewerUrlOrNull(spot.imageKeyViewer)
        // 저장된 medium 키만 사용한다(파생 금지). 미생성 사진은 null → 응답 imageUrlMedium 도 null 이 되어
        // 클라가 `medium ?? viewer` 폴백으로 viewer 를 쓴다(파생 시 객체 없는 구 사진에서 404 → blank 였음).
        val mediumUrl = if (suppressUrl) null else photoUrlSigner.signedMediumUrlOrNull(spot.imageKeyMedium)
        val thumbUrl = if (suppressUrl) null else photoUrlSigner.signedThumbUrlOrNull(spot.imageKeyThumb)
        return WalkPhotoResponse(
            id = spot.id,
            imageUrl = resolvedUrl,
            imageKey = rawKey,
            imageUrlViewer = viewerUrl,
            imageUrlMedium = mediumUrl,
            imageUrlThumb = thumbUrl,
            imageKeyViewer = spot.imageKeyViewer,
            imageKeyThumb = spot.imageKeyThumb,
            note = spot.note,
            walkDate = spot.walk.startTime,
            walkId = spot.walk.id,
            userId = spot.walk.user.id,
            petName = firstPet?.name,
            petImageUrl = firstPet?.profileImageUrl,
            hiddenFromPublic = spot.hiddenFromPublic
        )
    }

    @Transactional
    fun updateSpotVisibility(userId: Long, walkId: Long, spotId: Long, hiddenFromPublic: Boolean): WalkPhotoResponse {
        val walk = walkRepository.findById(walkId).orElseThrow { NotFoundException("Walk not found") }
        if (walk.user.id != userId) throw ForbiddenException("Not the owner of this walk")
        val spot = walkSpotRepository.findById(spotId).orElseThrow { NotFoundException("Spot not found") }
        if (spot.walk.id != walkId) throw ForbiddenException("Spot does not belong to this walk")
        spot.hiddenFromPublic = hiddenFromPublic
        return toWalkPhotoResponse(spot)
    }

    /**
     * community-author-profile-gallery Phase 2 F1 — 특정 유저의 공개 walk 사진을 cursor pagination 으로 반환.
     *
     * - 차단 필터(`findBlockedUserIds` native UNION 양방향) 로 viewer↔target 어느 쪽이든 차단 시 빈 결과.
     * - WalkSpot 기준 `(timestamp DESC, id DESC)` tuple cursor. Phase 1 community 패턴과 동일.
     * - `size+1` fetch 로 nextCursor 판정. 1..100 clamp, default 30.
     * - 갤러리 탭 전용 → `suppressUrl=false`. 서명 실패 시 기존 toWalkPhotoResponse 정책 (skip-in-DOM).
     */
    fun getUserWalkPhotos(
        viewerId: Long,
        targetUserId: Long,
        cursor: String?,
        size: Int = 30,
    ): UserWalkPhotosPage {
        val effectiveSize = size.coerceIn(1, 100)
        val blockedUserIds = userBlockRepository.findBlockedUserIds(viewerId)
        val effectiveBlockedIds = if (blockedUserIds.isEmpty()) listOf(-1L) else blockedUserIds

        val (cursorTimestamp, cursorId) = decodeWalkPhotoCursor(cursor)
            ?: (java.time.LocalDateTime.of(9999, 12, 31, 23, 59, 59) to Long.MAX_VALUE)

        val spots = walkSpotRepository.findPublicPhotoSpotsByAuthorWithCursor(
            authorId = targetUserId,
            blockedUserIds = effectiveBlockedIds,
            cursorTimestamp = cursorTimestamp,
            cursorId = cursorId,
            pageable = org.springframework.data.domain.PageRequest.of(0, effectiveSize + 1),
        )

        val hasNext = spots.size > effectiveSize
        val page = spots.take(effectiveSize)
        val nextCursor = if (hasNext) {
            val last = page.last()
            encodeWalkPhotoCursor(last.timestamp, last.id)
        } else null

        return UserWalkPhotosPage(
            photos = page.map { toWalkPhotoResponse(it, suppressUrl = false) },
            nextCursor = nextCursor,
        )
    }

    private fun encodeWalkPhotoCursor(ts: java.time.LocalDateTime, id: Long): String {
        val epochMs = ts.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val payload = "$epochMs:$id"
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
    }

    private fun decodeWalkPhotoCursor(cursor: String?): Pair<java.time.LocalDateTime, Long>? {
        if (cursor.isNullOrBlank()) return null
        try {
            val decoded = String(java.util.Base64.getUrlDecoder().decode(cursor))
            val parts = decoded.split(":")
            if (parts.size != 2) throw BadRequestException("INVALID_CURSOR")
            val epochMs = parts[0].toLong()
            val id = parts[1].toLong()
            val ts = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMs),
                java.time.ZoneOffset.UTC,
            )
            return ts to id
        } catch (ex: BadRequestException) {
            throw ex
        } catch (ex: Exception) {
            throw BadRequestException("INVALID_CURSOR")
        }
    }

    private fun parseYearMonth(yearMonth: String): Pair<java.time.LocalDateTime, java.time.LocalDateTime> {
        val parts = yearMonth.split("-")
        require(parts.size == 2) { "Invalid yearMonth format: $yearMonth" }
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val startOfMonth = java.time.LocalDateTime.of(year, month, 1, 0, 0, 0)
        return Pair(startOfMonth, startOfMonth.plusMonths(1))
    }

    /**
     * EXT-CDX-008 — path(GPS [위도, 경도] 좌표열)로 서버가 이동 거리를 재계산(Haversine 합산).
     * 좌표가 2개 미만이면 0km. 클라이언트가 보낸 distanceKm 는 사용하지 않는다.
     */
    private fun haversineDistanceKm(path: List<List<Double>>): Double {
        if (path.size < 2) return 0.0
        val earthRadiusKm = 6371.0088
        var total = 0.0
        for (i in 1 until path.size) {
            val prev = path[i - 1]
            val curr = path[i]
            if (prev.size < 2 || curr.size < 2) continue
            val lat1 = Math.toRadians(prev[0])
            val lon1 = Math.toRadians(prev[1])
            val lat2 = Math.toRadians(curr[0])
            val lon2 = Math.toRadians(curr[1])
            val dLat = lat2 - lat1
            val dLon = lon2 - lon1
            val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2).let { it * it }
            total += 2 * earthRadiusKm * Math.asin(Math.min(1.0, Math.sqrt(a)))
        }
        return total
    }

    private fun calculateWalkReward(distanceKm: Double): Int {
        val base = when {
            distanceKm < 0.5 -> 0       // 500m 미만: 보상 없음
            distanceKm < 1.0 -> 1       // 500m~1km: 1골드
            distanceKm < 3.0 -> 3       // 1km~3km: 3골드
            distanceKm < 5.0 -> 5       // 3km~5km: 5골드
            else -> 10                   // 5km 이상: 10골드
        }
        if (base == 0) return 0
        // ADR-001 보상 +20%: walk.reward.multiplier(기본 1.2) 곱 후 반올림
        val multiplier = systemSettingService.getString("walk.reward.multiplier", "1.2").toDoubleOrNull() ?: 1.2
        return Math.round(base * multiplier).toInt()
    }

    /**
     * 정적 지도 렌더용 경로 좌표. 조회와 인가 판단을 서비스가 소유하고, HTTP 상태 매핑은 호출부가 한다.
     *
     * ARCH-005: MapController 가 WalkRepository 를 직접 주입해 조회+공개여부 검사를 하고 있었다.
     * 404/403 을 구분해야 해서 결과를 타입으로 돌려준다.
     */
    sealed class MapPath {
        object NotFound : MapPath()
        object Forbidden : MapPath()
        class Ok(val coordinates: Array<org.locationtech.jts.geom.Coordinate>) : MapPath()
    }

    @Transactional(readOnly = true)
    fun getMapPath(walkId: Long, viewerId: Long): MapPath {
        val walk = walkRepository.findById(walkId).orElse(null) ?: return MapPath.NotFound
        // 공개 산책은 인증된 누구나, 비공개는 소유자만.
        if (walk.user.id != viewerId && !walk.isPublic) return MapPath.Forbidden
        return MapPath.Ok(walk.path.coordinates)
    }
}
