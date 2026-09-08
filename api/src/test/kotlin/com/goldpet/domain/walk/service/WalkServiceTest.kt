package com.goldpet.domain.walk.service

import com.goldpet.domain.gamification.service.BadgeAwardService
import com.goldpet.domain.gold.dto.TransactionResponse
import com.goldpet.domain.gold.entity.TransactionStatus
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.dto.CreateWalkRequest
import com.goldpet.domain.walk.dto.WalkSpotDto
import com.goldpet.domain.walk.entity.Walk
import com.goldpet.domain.walk.repository.BestWalkCoupleRepository
import com.goldpet.domain.walk.repository.WalkRepository
import com.goldpet.domain.walk.repository.WalkSpotRepository
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.common.repository.FileAttachmentRepository
import com.goldpet.infra.GeocodingService
import org.junit.jupiter.api.Assertions.*
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.time.LocalDateTime
import java.util.*
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.walk.entity.WalkSpot
import com.goldpet.domain.walk.entity.WalkSpotType
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class WalkServiceTest {

    private val meterRegistry = SimpleMeterRegistry()

    @Mock
    private lateinit var walkRepository: WalkRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var goldService: GoldService

    @Mock
    private lateinit var badgeAwardService: BadgeAwardService

    @Mock
    private lateinit var walkSpotRepository: WalkSpotRepository

    @Mock
    private lateinit var bestWalkCoupleRepository: BestWalkCoupleRepository

    @Mock
    private lateinit var petRepository: PetRepository

    @Mock
    private lateinit var userBlockRepository: UserBlockRepository

    @Mock
    private lateinit var geocodingService: GeocodingService

    @Mock
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var systemSettingService: SystemSettingService

    @Mock
    private lateinit var photoUrlSigner: PhotoUrlSigner

    @Mock
    private lateinit var fileAttachmentRepository: FileAttachmentRepository

    private lateinit var walkService: WalkService

    private lateinit var testUser: User
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // PERF-007: geocodingExecutor 는 테스트 결정성을 위해 호출 스레드에서 동기 실행하는 direct executor 사용.
        val geocodingExecutor = java.util.concurrent.Executor { it.run() }
        walkService = WalkService(walkRepository, walkSpotRepository, bestWalkCoupleRepository, userRepository, petRepository, goldService, badgeAwardService, geocodingService, applicationEventPublisher, systemSettingService, userBlockRepository, photoUrlSigner, meterRegistry, fileAttachmentRepository, geocodingExecutor)
        whenever(geocodingService.reverseGeocode(any(), any())).thenReturn("서울특별시 강남구")
        // ADR-001 보상 +20% 배수(walk.reward.multiplier). 보상 단언은 배수 적용 후 값 기준.
        whenever(systemSettingService.getString(eq("walk.reward.multiplier"), any())).thenReturn("1.2")
        // EXT-CDX-008 — Mockito 는 unstubbed getInt(...) 호출 시 0 을 반환한다(인자의 default 값이
        // 아니라 mock 자체의 기본값). 상한류 키를 스텁하지 않으면 모든 walk 가 "상한 0 초과"로
        // 거부되므로, 프로덕션 기본값과 동일하게 명시 스텁한다.
        whenever(systemSettingService.getInt(eq("walk.max.speed.kmh"), any())).thenReturn(30)
        whenever(systemSettingService.getInt(eq("walk.duration.wallclock.tolerance.seconds"), any())).thenReturn(10)
        whenever(systemSettingService.getInt(eq("walk.max.distance.km"), any())).thenReturn(50)
        whenever(systemSettingService.getInt(eq("walk.max.duration.seconds"), any())).thenReturn(21_600)

        testUser = createTestUser(1L, "testuser", "테스트유저")
    }

    // EXT-CDX-008 — 목표 거리(km)에 맞는 자오선(경도 고정) path 를 생성. 서버 Haversine 재계산이
    // 이 거리와 근사해야 한다. 6371.0088km * π/180 ≈ 111.1949 km/deg.
    private fun pathForKm(km: Double, points: Int = 2): List<List<Double>> {
        val baseLat = 37.5
        val lon = 127.0
        val totalDeltaLat = km / 111.1949
        return (0 until points).map { i ->
            listOf(baseLat + totalDeltaLat * i / (points - 1), lon)
        }
    }

    private fun createTestUser(id: Long, oauthId: String, nickname: String): User {
        return User(
            id = id,
            email = "$oauthId@example.com",
            oauthProvider = "LOCAL",
            oauthId = oauthId,
            username = oauthId,
            password = "password",
            nickname = nickname,
            name = nickname,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )
    }

    private fun mockTransactionResponse(amount: Int): TransactionResponse {
        return TransactionResponse(
            id = 1L,
            type = TransactionType.REWARD,
            amount = amount,
            balanceAfter = 100,
            description = "산책 보상",
            status = TransactionStatus.COMPLETED,
            createdAt = LocalDateTime.now()
        )
    }

    private fun createWalkRequest(distanceKm: Double): CreateWalkRequest {
        return CreateWalkRequest(
            startTime = LocalDateTime.now().minusHours(1),
            endTime = LocalDateTime.now(),
            distanceKm = distanceKm,
            durationSeconds = 3600,
            // EXT-CDX-008 — path 가 실제 거리 진실이므로 목표 거리에 맞춰 생성.
            path = pathForKm(distanceKm),
            caloriesBurned = null,
            notes = null
        )
    }

    private fun mockWalkSave() {
        whenever(walkRepository.save(any<Walk>())).thenAnswer { invocation ->
            val walk = invocation.getArgument<Walk>(0)
            Walk(
                id = 1L,
                user = walk.user,
                startTime = walk.startTime,
                endTime = walk.endTime,
                distanceKm = walk.distanceKm,
                durationSeconds = walk.durationSeconds,
                path = walk.path,
                caloriesBurned = walk.caloriesBurned,
                notes = walk.notes
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
                walk.spots.forEach { spot -> spots.add(spot) }
            }
        }
    }

    @Test
    fun `createWalk should create walk with path and spots`() {
        val startTime = LocalDateTime.now().minusHours(1)
        val endTime = LocalDateTime.now()
        val request = CreateWalkRequest(
            startTime = startTime,
            endTime = endTime,
            distanceKm = 2.5,
            durationSeconds = 3600,
            // EXT-CDX-008 — 서버 재계산이 ~2.5km 가 되도록 3점 자오선 path 구성.
            path = pathForKm(2.5, points = 3),
            spots = listOf(
                WalkSpotDto(
                    latitude = 37.5668,
                    longitude = 126.9783,
                    type = WalkSpotType.PEE,
                    timestamp = startTime.plusMinutes(30),
                    imageUrl = null,
                    note = "공원 근처"
                )
            ),
            caloriesBurned = 150.0,
            notes = "오늘 산책 좋았음"
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        mockWalkSave()
        whenever(goldService.grantReward(any(), any(), any())).thenReturn(mockTransactionResponse(3))
        whenever(badgeAwardService.checkAndAwardBadges(any(), any())).thenReturn(emptyList())

        val result = walkService.createWalk(1L, request)

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals(1L, result.userId)
        // EXT-CDX-008 — distanceKm 은 클라이언트 값(2.5)이 아니라 서버 path 재계산값(≈2.5).
        assertEquals(2.5, result.distanceKm, 0.05)
        assertEquals(3600, result.durationSeconds)
        assertEquals(150.0, result.caloriesBurned)
        assertEquals("오늘 산책 좋았음", result.notes)
        assertEquals(3, result.path.size)
        assertEquals(1, result.spots.size)
        assertEquals(com.goldpet.domain.walk.entity.WalkSpotType.PEE, result.spots[0].type)
    }

    @Test
    fun `createWalk should throw exception when user not found`() {
        val request = createWalkRequest(2.5)
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            walkService.createWalk(999L, request)
        }
    }

    @Test
    fun `getWalk should return walk when exists`() {
        val lineString = geometryFactory.createLineString(arrayOf(
            Coordinate(126.9780, 37.5665),
            Coordinate(126.9785, 37.5670),
            Coordinate(126.9790, 37.5675)
        ))

        val walk = Walk(
            id = 1L,
            user = testUser,
            startTime = LocalDateTime.now().minusHours(1),
            endTime = LocalDateTime.now(),
            distanceKm = 2.5,
            durationSeconds = 3600,
            path = lineString,
            caloriesBurned = 150.0,
            notes = "좋은 산책"
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(walkRepository.findById(1L)).thenReturn(Optional.of(walk))

        val result = walkService.getWalk(1L, 1L)

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals(2.5, result.distanceKm)
        assertEquals(3600, result.durationSeconds)
    }

    @Test
    fun `getWalk should throw exception when walk not found`() {
        whenever(walkRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            walkService.getWalk(999L, null)
        }
    }

    @Test
    fun `getWalksByUser should return list of walks`() {
        val lineString = geometryFactory.createLineString(arrayOf(
            Coordinate(126.9780, 37.5665),
            Coordinate(126.9785, 37.5670)
        ))

        val walk1 = Walk(
            id = 1L,
            user = testUser,
            startTime = LocalDateTime.now().minusDays(1),
            endTime = LocalDateTime.now().minusDays(1).plusHours(1),
            distanceKm = 2.0,
            durationSeconds = 3600,
            path = lineString,
            caloriesBurned = null,
            notes = null
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        val walk2 = Walk(
            id = 2L,
            user = testUser,
            startTime = LocalDateTime.now().minusHours(2),
            endTime = LocalDateTime.now().minusHours(1),
            distanceKm = 3.0,
            durationSeconds = 3600,
            path = lineString,
            caloriesBurned = null,
            notes = null
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(walkRepository.findByUserIdOrderByStartTimeDesc(1L)).thenReturn(listOf(walk1, walk2))

        val result = walkService.getWalksByUser(1L)

        assertEquals(2, result.size)
        assertEquals(2.0, result[0].distanceKm)
        assertEquals(3.0, result[1].distanceKm)
    }

    @Test
    fun `createWalk should handle badge award error gracefully`() {
        val request = createWalkRequest(2.5)

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        mockWalkSave()
        whenever(goldService.grantReward(any(), any(), any())).thenReturn(mockTransactionResponse(3))
        whenever(badgeAwardService.checkAndAwardBadges(any(), any()))
            .thenThrow(RuntimeException("Badge award service error"))

        val result = walkService.createWalk(1L, request)

        assertNotNull(result)
        assertEquals(1L, result.id)
    }

    // === Gold Reward Tests ===

    @Test
    fun `createWalk should grant gold reward for walk over 0_5km`() {
        val request = createWalkRequest(1.5) // 1km~3km = base 3 골드 × 1.2 = 4 (round)

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        mockWalkSave()
        whenever(goldService.grantReward(any(), any(), any())).thenReturn(mockTransactionResponse(4))
        whenever(badgeAwardService.checkAndAwardBadges(any(), any())).thenReturn(emptyList())

        walkService.createWalk(1L, request)

        verify(goldService).grantReward(eq(1L), eq(4), argThat { contains("1.5km") })
    }

    @Test
    fun `createWalk should not grant gold reward for walk under 0_5km`() {
        val request = createWalkRequest(0.3) // < 0.5km = no reward

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        mockWalkSave()
        whenever(badgeAwardService.checkAndAwardBadges(any(), any())).thenReturn(emptyList())

        walkService.createWalk(1L, request)

        verify(goldService, never()).grantReward(any(), any(), any())
    }

    @Test
    fun `createWalk should grant 12 gold for walk over 5km`() {
        val request = createWalkRequest(7.2) // >= 5km = base 10 골드 × 1.2 = 12

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        mockWalkSave()
        whenever(goldService.grantReward(any(), any(), any())).thenReturn(mockTransactionResponse(12))
        whenever(badgeAwardService.checkAndAwardBadges(any(), any())).thenReturn(emptyList())

        walkService.createWalk(1L, request)

        verify(goldService).grantReward(eq(1L), eq(12), any())
    }

    @Test
    fun `createWalk should grant 1 gold for walk between 0_5km and 1km`() {
        val request = createWalkRequest(0.7) // 0.5~1km = 1 gold

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        mockWalkSave()
        whenever(goldService.grantReward(any(), any(), any())).thenReturn(mockTransactionResponse(1))
        whenever(badgeAwardService.checkAndAwardBadges(any(), any())).thenReturn(emptyList())

        walkService.createWalk(1L, request)

        verify(goldService).grantReward(eq(1L), eq(1), any())
    }

    @Test
    fun `createWalk ignores inflated client distanceKm and rewards from server path distance`() {
        // 클라이언트가 distanceKm=100 으로 부풀려도 path 는 ~1.5km → 보상/거리 모두 서버 path 기준.
        val request = CreateWalkRequest(
            startTime = LocalDateTime.now().minusHours(1),
            endTime = LocalDateTime.now(),
            distanceKm = 100.0, // 조작된 클라이언트 값
            durationSeconds = 3600,
            path = pathForKm(1.5),
            caloriesBurned = null,
            notes = null
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        mockWalkSave()
        whenever(goldService.grantReward(any(), any(), any())).thenReturn(mockTransactionResponse(4))
        whenever(badgeAwardService.checkAndAwardBadges(any(), any())).thenReturn(emptyList())

        val result = walkService.createWalk(1L, request)

        // 보상은 1.5km 기준(base 3 × 1.2 = 4)이지 100km 기준이 아니다.
        verify(goldService).grantReward(eq(1L), eq(4), any())
        assertEquals(1.5, result.distanceKm, 0.05, "저장 거리는 서버 path 재계산값이어야 한다")
    }

    @Test
    fun `createWalk rejects implausible speed (path fabrication)`() {
        // 60초에 ~50km → 3000km/h. 상한(30km/h) 초과로 거부.
        val request = CreateWalkRequest(
            startTime = LocalDateTime.now().minusMinutes(1),
            endTime = LocalDateTime.now(),
            distanceKm = 50.0,
            durationSeconds = 60,
            path = pathForKm(50.0),
            caloriesBurned = null,
            notes = null
        )

        assertThrows<BadRequestException> { walkService.createWalk(1L, request) }
        verify(goldService, never()).grantReward(any(), any(), any())
    }

    // === W-1 (EXT-CDX-008 적대적 리뷰): durationSeconds wall-clock 우회 방어 ===
    //
    // 근거(app/lib/features/walk/mixins/walk_tracking_mixin.dart:676-677, 847-848):
    //   durationSeconds = (endTime - startTime - totalPauseDuration).inSeconds
    // pauseDuration >= 0 이므로 정상 클라이언트는 durationSeconds <= wallClockSeconds(=endTime-startTime)
    // 를 항상 만족한다 — "멈춤"은 durationSeconds 를 wall-clock보다 작게 만드는 방향으로만 작용한다.
    // 아래 D 케이스가 바로 그 정상 경로(멈춤 포함 장시간 산책)이며 거부되면 안 된다.

    @Test
    fun `createWalk rejects forged duration that inflates wall-clock to slip under the speed cap`() {
        // 위조: 실제 제출 구간(wall-clock)은 60초뿐인데 durationSeconds 만 12000초(≈3.33h)로 부풀려
        // 100km/12000s ≈ 30km/h(상한 이내)로 보이게 만드는 공격. wall-clock 교차검증이 먼저 차단해야 한다.
        val request = CreateWalkRequest(
            startTime = LocalDateTime.now().minusSeconds(60),
            endTime = LocalDateTime.now(),
            distanceKm = 100.0,
            durationSeconds = 12000,
            path = pathForKm(100.0),
            caloriesBurned = null,
            notes = null
        )

        val exception = assertThrows<BadRequestException> { walkService.createWalk(1L, request) }
        assertTrue(
            exception.message!!.contains("경과시간"),
            "wall-clock 교차검증 실패로 거부되어야 한다 (실제: ${exception.message})",
        )
        verify(goldService, never()).grantReward(any(), any(), any())
    }

    @Test
    fun `createWalk rejects distance exceeding the per-walk hard cap even with internally consistent duration`() {
        // durationSeconds 가 wall-clock 과 정확히 일치하고(교차검증 통과) 평균속도(27km/h)도
        // 상한(30km/h) 이내라도, 거리(60km) 자체가 1회 산책 현실적 상한(50km)을 넘으면 거부한다.
        val request = CreateWalkRequest(
            startTime = LocalDateTime.now().minusSeconds(8000),
            endTime = LocalDateTime.now(),
            distanceKm = 60.0,
            durationSeconds = 8000,
            path = pathForKm(60.0),
            caloriesBurned = null,
            notes = null
        )

        val exception = assertThrows<BadRequestException> { walkService.createWalk(1L, request) }
        assertTrue(
            exception.message!!.contains("거리 상한"),
            "거리 상한 초과로 거부되어야 한다 (실제: ${exception.message})",
        )
        verify(goldService, never()).grantReward(any(), any(), any())
    }

    @Test
    fun `createWalk rejects duration exceeding the per-walk hard cap even with low speed`() {
        // 거리(10km)·평균속도(1.44km/h) 모두 낮고 wall-clock 과도 정합되지만, 산책 시간(25000초
        // ≈6.9h) 자체가 1회 상한(21600초=6h)을 넘으면 거부한다.
        val request = CreateWalkRequest(
            startTime = LocalDateTime.now().minusSeconds(25_000),
            endTime = LocalDateTime.now(),
            distanceKm = 10.0,
            durationSeconds = 25_000,
            path = pathForKm(10.0),
            caloriesBurned = null,
            notes = null
        )

        val exception = assertThrows<BadRequestException> { walkService.createWalk(1L, request) }
        assertTrue(
            exception.message!!.contains("시간 상한"),
            "시간 상한 초과로 거부되어야 한다 (실제: ${exception.message})",
        )
        verify(goldService, never()).grantReward(any(), any(), any())
    }

    @Test
    fun `createWalk accepts a realistic walk with a pause where durationSeconds is well under wall-clock`() {
        // 정상 회귀: wall-clock 2시간(7200초) 중 40분(2400초) 멈춤 → durationSeconds=4800초(80분).
        // durationSeconds(4800) < wallClockSeconds(7200) 방향이므로 거부되면 안 된다(클라 공식과 일치).
        val request = CreateWalkRequest(
            startTime = LocalDateTime.now().minusSeconds(7200),
            endTime = LocalDateTime.now(),
            distanceKm = 4.0,
            durationSeconds = 4800,
            path = pathForKm(4.0),
            caloriesBurned = null,
            notes = null
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        mockWalkSave()
        whenever(goldService.grantReward(any(), any(), any())).thenReturn(mockTransactionResponse(6))
        whenever(badgeAwardService.checkAndAwardBadges(any(), any())).thenReturn(emptyList())

        val result = walkService.createWalk(1L, request)

        assertEquals(4.0, result.distanceKm, 0.05, "멈춤 포함 정상 산책은 거부되지 않고 서버 거리로 저장되어야 한다")
        // 3km~5km 구간 base 5 × 1.2 배수 = 6골드(정상 보상 지급 확인 — 안전한 방향의 duration 은 페널티 없음).
        verify(goldService).grantReward(eq(1L), eq(6), any())
    }

    @Test
    fun `getMyStats should return total gold earned via GoldService`() {
        whenever(walkRepository.sumDistanceByUserId(1L)).thenReturn(10.0)
        whenever(walkRepository.sumDurationByUserId(1L)).thenReturn(7200L)
        whenever(walkRepository.sumCaloriesByUserId(1L)).thenReturn(500.0)
        whenever(walkRepository.countByUserId(1L)).thenReturn(5)
        whenever(goldService.getTotalRewardAmount(1L)).thenReturn(30)

        val result = walkService.getMyStats(1L)

        assertEquals(10.0, result.totalDistanceKm)
        assertEquals(120L, result.totalDurationMinutes)
        assertEquals(500.0, result.totalCalories)
        assertEquals(5, result.totalWalks)
        assertEquals(30, result.totalGoldEarned)
        verify(goldService).getTotalRewardAmount(1L)
    }

    // === Presigned URL wiring ===

    private fun buildPhotoSpot(
        imageKey: String,
        hiddenFromPublic: Boolean,
        imageKeyViewer: String? = null,
        imageKeyMedium: String? = null
    ): WalkSpot {
        val walk = Walk(
            id = 10L,
            user = testUser,
            startTime = LocalDateTime.now().minusHours(1),
            endTime = LocalDateTime.now(),
            distanceKm = 1.0,
            durationSeconds = 600,
            path = geometryFactory.createLineString(arrayOf(
                Coordinate(126.9780, 37.5665),
                Coordinate(126.9785, 37.5670)
            )),
            caloriesBurned = null,
            notes = null
        )
        val point = geometryFactory.createPoint(Coordinate(126.9781, 37.5666))
        return WalkSpot(
            id = 99L,
            walk = walk,
            location = point,
            type = WalkSpotType.PHOTO,
            timestamp = LocalDateTime.now(),
            imageUrl = imageKey,
            note = null,
            hiddenFromPublic = hiddenFromPublic,
            imageKeyViewer = imageKeyViewer,
            imageKeyMedium = imageKeyMedium
        )
    }

    @Test
    fun `getMyPhotos signs the stored medium key into imageUrlMedium`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val viewerKey = "a1b2c3d4-e5f6-7890-abcd-ef0123456789_viewer.jpg"
        val mediumKey = "a1b2c3d4-e5f6-7890-abcd-ef0123456789_medium.jpg"
        val signedMedium = "https://minio.example.com/goldpet-private/$mediumKey?X-Amz-Signature=med"
        val spot = buildPhotoSpot(key, hiddenFromPublic = false, imageKeyViewer = viewerKey, imageKeyMedium = mediumKey)

        whenever(photoUrlSigner.signedUrlOrNull(key)).thenReturn(null)
        whenever(photoUrlSigner.signedMediumUrlOrNull(mediumKey)).thenReturn(signedMedium)
        whenever(walkSpotRepository.findPhotoSpotsByUserId(eq(1L), any()))
            .thenReturn(PageImpl(listOf(spot)))

        val result = walkService.getMyPhotos(1L, null, PageRequest.of(0, 20))

        assertEquals(signedMedium, result.content.first().imageUrlMedium, "stored medium key must be signed")
        verify(photoUrlSigner).signedMediumUrlOrNull(mediumKey)
    }

    // 회귀: medium 변형이 없는 구 사진(viewer 는 있음)은 imageUrlMedium 이 null 이어야 한다.
    // 이전에는 viewer 키에서 medium 을 파생해 객체 없는 404 URL 을 내보냈고, 클라의 `medium ?? viewer`
    // 폴백이 non-null URL 이라 걸리지 않아 큰 사진이 blank 였다. 파생을 제거해 null 로 내보낸다.
    @Test
    fun `getMyPhotos leaves imageUrlMedium null for legacy photo without medium variant`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val viewerKey = "a1b2c3d4-e5f6-7890-abcd-ef0123456789_viewer.jpg"
        // 구 사진: viewer 는 있으나 medium 미생성(백필 전) → imageKeyMedium == null
        val spot = buildPhotoSpot(key, hiddenFromPublic = false, imageKeyViewer = viewerKey, imageKeyMedium = null)

        whenever(photoUrlSigner.signedUrlOrNull(key)).thenReturn(null)
        whenever(photoUrlSigner.signedMediumUrlOrNull(null)).thenReturn(null)
        whenever(walkSpotRepository.findPhotoSpotsByUserId(eq(1L), any()))
            .thenReturn(PageImpl(listOf(spot)))

        val result = walkService.getMyPhotos(1L, null, PageRequest.of(0, 20))

        assertNull(result.content.first().imageUrlMedium, "medium 미생성 구 사진은 404 URL 대신 null 이어야 한다")
        // viewer 키에서 파생한 `_medium` 키로 서명을 시도하면 안 된다(404 URL 생성 금지).
        verify(photoUrlSigner, never()).signedMediumUrlOrNull("a1b2c3d4-e5f6-7890-abcd-ef0123456789_medium.jpg")
    }

    @Test
    fun `getMyPhotos leaves imageUrlMedium null when viewer key absent`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val spot = buildPhotoSpot(key, hiddenFromPublic = false, imageKeyViewer = null)

        whenever(photoUrlSigner.signedUrlOrNull(key)).thenReturn(null)
        whenever(photoUrlSigner.signedMediumUrlOrNull(null)).thenReturn(null)
        whenever(walkSpotRepository.findPhotoSpotsByUserId(eq(1L), any()))
            .thenReturn(PageImpl(listOf(spot)))

        val result = walkService.getMyPhotos(1L, null, PageRequest.of(0, 20))

        assertNull(result.content.first().imageUrlMedium, "no viewer key → no derived medium key → null")
    }

    @Test
    fun `getMyPhotos signs URL when flag on and returns imageKey alongside`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val signedUrl = "https://minio.example.com/goldpet-private/$key?X-Amz-Signature=abc"
        val spot = buildPhotoSpot(key, hiddenFromPublic = false)

        whenever(photoUrlSigner.signedUrlOrNull(key)).thenReturn(signedUrl)
        whenever(walkSpotRepository.findPhotoSpotsByUserId(eq(1L), any()))
            .thenReturn(PageImpl(listOf(spot)))

        val result = walkService.getMyPhotos(1L, null, PageRequest.of(0, 20))

        assertEquals(1, result.content.size)
        val photo = result.content.first()
        assertEquals(signedUrl, photo.imageUrl)
        assertEquals(key, photo.imageKey)
    }

    @Test
    fun `getMyPhotos falls back to raw key when flag off`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val spot = buildPhotoSpot(key, hiddenFromPublic = false)

        whenever(photoUrlSigner.signedUrlOrNull(key)).thenReturn(null)
        whenever(walkSpotRepository.findPhotoSpotsByUserId(eq(1L), any()))
            .thenReturn(PageImpl(listOf(spot)))

        val result = walkService.getMyPhotos(1L, null, PageRequest.of(0, 20))

        val photo = result.content.first()
        // T1-4: rawKey fallback 제거. signing 실패 시 imageUrl=null (클라이언트 skip)
        assertEquals(null, photo.imageUrl, "flag-off path returns null after T1-4 (no rawKey fallback)")
        assertEquals(key, photo.imageKey)
    }

    @Test
    fun `getPublicPhotos suppresses URL when hiddenFromPublic is true`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val hiddenSpot = buildPhotoSpot(key, hiddenFromPublic = true)

        whenever(userBlockRepository.findBlockedUserIds(1L)).thenReturn(emptyList())
        whenever(walkSpotRepository.findPublicPhotoSpots(any(), any()))
            .thenReturn(PageImpl(listOf(hiddenSpot)))

        val result = walkService.getPublicPhotos(1L, PageRequest.of(0, 20))

        val photo = result.content.first()
        assertNull(photo.imageUrl, "hidden photo in public listing must have URL suppressed")
        assertEquals(key, photo.imageKey, "imageKey is still emitted so frontend can mint on demand")
        verify(photoUrlSigner, never()).signedUrlOrNull(any())
    }

    @Test
    fun `getPublicPhotos signs URL for non-hidden photo`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val signedUrl = "https://minio.example.com/goldpet-private/$key?X-Amz-Signature=xyz"
        val spot = buildPhotoSpot(key, hiddenFromPublic = false)

        whenever(userBlockRepository.findBlockedUserIds(1L)).thenReturn(emptyList())
        whenever(photoUrlSigner.signedUrlOrNull(key)).thenReturn(signedUrl)
        whenever(walkSpotRepository.findPublicPhotoSpots(any(), any()))
            .thenReturn(PageImpl(listOf(spot)))

        val result = walkService.getPublicPhotos(1L, PageRequest.of(0, 20))

        val photo = result.content.first()
        assertEquals(signedUrl, photo.imageUrl)
        assertEquals(key, photo.imageKey)
    }

    // === §3.7 mint endpoint ===

    private fun buildPublicPhotoSpot(
        imageKey: String,
        owner: User,
        hiddenFromPublic: Boolean,
        isPublic: Boolean = true
    ): WalkSpot {
        val walk = Walk(
            id = 10L,
            user = owner,
            startTime = LocalDateTime.now().minusHours(1),
            endTime = LocalDateTime.now(),
            distanceKm = 1.0,
            durationSeconds = 600,
            path = geometryFactory.createLineString(arrayOf(
                Coordinate(126.9780, 37.5665),
                Coordinate(126.9785, 37.5670)
            )),
            caloriesBurned = null,
            notes = null,
            isPublic = isPublic
        )
        return WalkSpot(
            id = 77L,
            walk = walk,
            location = geometryFactory.createPoint(Coordinate(126.9781, 37.5666)),
            type = WalkSpotType.PHOTO,
            timestamp = LocalDateTime.now(),
            imageUrl = imageKey,
            note = null,
            hiddenFromPublic = hiddenFromPublic
        )
    }

    @Test
    fun `mintPhotoUrl returns signed URL and imageKey for owner`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val signedUrl = "https://minio.example.com/goldpet-private/$key?X-Amz-Signature=abc"
        val spot = buildPublicPhotoSpot(key, owner = testUser, hiddenFromPublic = false)

        whenever(walkSpotRepository.findById(77L)).thenReturn(Optional.of(spot))
        whenever(photoUrlSigner.signedUrlOrNull(key)).thenReturn(signedUrl)

        val response = walkService.mintPhotoUrl(77L, testUser.id)

        assertEquals(77L, response.spotId)
        assertEquals(signedUrl, response.imageUrl)
        assertEquals(key, response.imageKey)
        assertTrue(response.expiresAt.isAfter(LocalDateTime.now().plusMinutes(25)))
    }

    @Test
    fun `mintPhotoUrl falls back to raw key when signer returns null (flag off)`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val spot = buildPublicPhotoSpot(key, owner = testUser, hiddenFromPublic = false)

        whenever(walkSpotRepository.findById(77L)).thenReturn(Optional.of(spot))
        whenever(photoUrlSigner.signedUrlOrNull(key)).thenReturn(null)

        val response = walkService.mintPhotoUrl(77L, testUser.id)

        // T1-4: rawKey fallback 제거. signing 실패 시 imageUrl=null.
        assertEquals(null, response.imageUrl, "flag-off path returns null after T1-4 (no rawKey fallback)")
        assertEquals(key, response.imageKey)
    }

    @Test
    fun `mintPhotoUrl allows non-owner viewer on public, not-hidden photo`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val signedUrl = "https://minio.example.com/goldpet-private/$key?X-Amz-Signature=xyz"
        val owner = createTestUser(1L, "owner", "소유자")
        val viewer = createTestUser(2L, "viewer", "뷰어")
        val spot = buildPublicPhotoSpot(key, owner = owner, hiddenFromPublic = false)

        whenever(walkSpotRepository.findById(77L)).thenReturn(Optional.of(spot))
        whenever(userBlockRepository.findBlockedUserIds(viewer.id)).thenReturn(emptyList())
        whenever(photoUrlSigner.signedUrlOrNull(key)).thenReturn(signedUrl)

        val response = walkService.mintPhotoUrl(77L, viewer.id)

        assertEquals(signedUrl, response.imageUrl)
        assertEquals(key, response.imageKey)
    }

    @Test
    fun `mintPhotoUrl throws ForbiddenException when non-owner requests hidden photo`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val owner = createTestUser(1L, "owner", "소유자")
        val viewer = createTestUser(2L, "viewer", "뷰어")
        val spot = buildPublicPhotoSpot(key, owner = owner, hiddenFromPublic = true)

        whenever(walkSpotRepository.findById(77L)).thenReturn(Optional.of(spot))

        assertThrows<ForbiddenException> { walkService.mintPhotoUrl(77L, viewer.id) }
        verify(photoUrlSigner, never()).signedUrlOrNull(any())
    }

    @Test
    fun `mintPhotoUrl throws ForbiddenException when non-owner requests private walk`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val owner = createTestUser(1L, "owner", "소유자")
        val viewer = createTestUser(2L, "viewer", "뷰어")
        val spot = buildPublicPhotoSpot(key, owner = owner, hiddenFromPublic = false, isPublic = false)

        whenever(walkSpotRepository.findById(77L)).thenReturn(Optional.of(spot))

        assertThrows<ForbiddenException> { walkService.mintPhotoUrl(77L, viewer.id) }
        verify(photoUrlSigner, never()).signedUrlOrNull(any())
    }

    @Test
    fun `mintPhotoUrl throws ForbiddenException when viewer is blocked by owner`() {
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val owner = createTestUser(1L, "owner", "소유자")
        val viewer = createTestUser(2L, "viewer", "뷰어")
        val spot = buildPublicPhotoSpot(key, owner = owner, hiddenFromPublic = false)

        whenever(walkSpotRepository.findById(77L)).thenReturn(Optional.of(spot))
        whenever(userBlockRepository.findBlockedUserIds(viewer.id)).thenReturn(listOf(owner.id))

        assertThrows<ForbiddenException> { walkService.mintPhotoUrl(77L, viewer.id) }
        verify(photoUrlSigner, never()).signedUrlOrNull(any())
    }

    @Test
    fun `mintPhotoUrl throws NotFoundException for non-existent spot`() {
        whenever(walkSpotRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> { walkService.mintPhotoUrl(999L, 1L) }
    }
}
