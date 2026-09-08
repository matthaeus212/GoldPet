package com.goldpet.domain.checkin.service

import com.goldpet.domain.checkin.dto.CreateCheckInRequest
import com.goldpet.domain.checkin.dto.CreatePlaceRequest
import com.goldpet.domain.checkin.dto.NearbyPlaceRequest
import com.goldpet.domain.checkin.entity.CheckIn
import com.goldpet.domain.checkin.entity.Place
import com.goldpet.domain.checkin.entity.PlaceCategory
import com.goldpet.domain.checkin.repository.CheckInRepository
import com.goldpet.domain.checkin.repository.PlaceRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.common.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.*

class CheckInServiceTest {

    @Mock
    private lateinit var placeRepository: PlaceRepository

    @Mock
    private lateinit var checkInRepository: CheckInRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var badgeAwardService: com.goldpet.domain.gamification.service.BadgeAwardService

    private lateinit var checkInService: CheckInService

    private lateinit var testUser: User
    private lateinit var testPlace: Place
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        checkInService = CheckInService(placeRepository, checkInRepository, userRepository, badgeAwardService)

        testUser = createTestUser(1L, "testuser", "테스트유저")

        val point = geometryFactory.createPoint(Coordinate(126.9780, 37.5665))
        testPlace = Place(
            id = 1L,
            name = "서울숲공원",
            category = PlaceCategory.PARK,
            address = "서울시 성동구 뚝섬로 273",
            locationGeom = point,
            description = "서울숲공원입니다"
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
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

    @Test
    fun `getNearbyPlaces should return places within radius`() {
        // Given
        val request = NearbyPlaceRequest(
            latitude = 37.5665,
            longitude = 126.9780,
            radiusMeters = 1000.0
        )

        whenever(placeRepository.findNearbyPlaces(37.5665, 126.9780, 1000.0)).thenReturn(listOf(testPlace))

        // When
        val result = checkInService.getNearbyPlaces(request)

        // Then
        assertEquals(1, result.size)
        assertEquals("서울숲공원", result[0].name)
    }

    @Test
    fun `getPlaces should return paginated places`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl(listOf(testPlace), pageable, 1)

        whenever(placeRepository.findAllByDeletedAtIsNull(pageable)).thenReturn(page)

        // When
        val result = checkInService.getPlaces(null, pageable)

        // Then
        assertEquals(1, result.content.size)
        assertEquals("서울숲공원", result.content[0].name)
    }

    @Test
    fun `getPlaces should filter by category`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl(listOf(testPlace), pageable, 1)

        whenever(placeRepository.findByCategoryAndDeletedAtIsNull(PlaceCategory.PARK, pageable)).thenReturn(page)

        // When
        val result = checkInService.getPlaces(PlaceCategory.PARK, pageable)

        // Then
        assertEquals(1, result.content.size)
        assertEquals(PlaceCategory.PARK, result.content[0].category)
    }

    @Test
    fun `getPlace should return place when exists`() {
        // Given
        whenever(placeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(testPlace)

        // When
        val result = checkInService.getPlace(1L)

        // Then
        assertEquals("서울숲공원", result.name)
        assertEquals(PlaceCategory.PARK, result.category)
    }

    @Test
    fun `getPlace should throw exception when not found`() {
        // Given
        whenever(placeRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(null)

        // When & Then
        assertThrows<NotFoundException> {
            checkInService.getPlace(999L)
        }
    }

    @Test
    fun `searchPlaces should return matching places`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl(listOf(testPlace), pageable, 1)

        whenever(placeRepository.findByNameContainingIgnoreCaseAndDeletedAtIsNull("서울", pageable)).thenReturn(page)

        // When
        val result = checkInService.searchPlaces("서울", pageable)

        // Then
        assertEquals(1, result.content.size)
        assertTrue(result.content[0].name.contains("서울"))
    }

    @Test
    fun `createPlace should create new place`() {
        // Given
        val request = CreatePlaceRequest(
            name = "새로운 공원",
            category = PlaceCategory.PARK,
            address = "서울시 강남구",
            latitude = 37.5000,
            longitude = 127.0000,
            description = "새 공원입니다"
        )

        whenever(placeRepository.save(any<Place>())).thenAnswer { invocation ->
            val place = invocation.getArgument<Place>(0)
            Place(
                id = 2L,
                name = place.name,
                category = place.category,
                address = place.address,
                locationGeom = place.locationGeom,
                description = place.description
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = checkInService.createPlace(request)

        // Then
        assertEquals("새로운 공원", result.name)
        assertEquals(PlaceCategory.PARK, result.category)
    }

    @Test
    fun `checkIn should create check-in`() {
        // Given
        val request = CreateCheckInRequest(
            placeId = 1L,
            memo = "좋은 곳이에요!"
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(placeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(testPlace)
        whenever(checkInRepository.existsByUserIdAndPlaceIdAndCreatedAtAfter(any(), any(), any())).thenReturn(false)
        whenever(placeRepository.save(any<Place>())).thenReturn(testPlace)
        whenever(checkInRepository.save(any<CheckIn>())).thenAnswer { invocation ->
            val checkIn = invocation.getArgument<CheckIn>(0)
            CheckIn(
                id = 1L,
                user = checkIn.user,
                place = checkIn.place,
                photoUrl = checkIn.photoUrl,
                memo = checkIn.memo
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = checkInService.checkIn(1L, request)

        // Then
        assertNotNull(result)
        assertEquals("서울숲공원", result.placeName)
        assertEquals("좋은 곳이에요!", result.memo)
    }

    @Test
    fun `checkIn should throw exception when duplicate within 1 hour`() {
        // Given
        val request = CreateCheckInRequest(placeId = 1L)

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(placeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(testPlace)
        whenever(checkInRepository.existsByUserIdAndPlaceIdAndCreatedAtAfter(any(), any(), any())).thenReturn(true)

        // When & Then
        assertThrows<ConflictException> {
            checkInService.checkIn(1L, request)
        }
    }

    @Test
    fun `getMyCheckIns should return user check-ins`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val checkIn = CheckIn(
            id = 1L,
            user = testUser,
            place = testPlace,
            memo = "좋아요"
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        val page = PageImpl(listOf(checkIn), pageable, 1)

        whenever(checkInRepository.findAllByUserIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(page)

        // When
        val result = checkInService.getMyCheckIns(1L, pageable)

        // Then
        assertEquals(1, result.content.size)
    }

    @Test
    fun `getPlaceCheckIns should return place check-ins`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val checkIn = CheckIn(
            id = 1L,
            user = testUser,
            place = testPlace,
            memo = "좋아요"
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        val page = PageImpl(listOf(checkIn), pageable, 1)

        whenever(checkInRepository.findAllByPlaceIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(page)

        // When
        val result = checkInService.getPlaceCheckIns(1L, pageable)

        // Then
        assertEquals(1, result.content.size)
    }

    @Test
    fun `getCheckInCount should return user check-in count within 30 days`() {
        // Given
        whenever(checkInRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(10L)

        // When
        val result = checkInService.getCheckInCount(1L)

        // Then
        assertEquals(10L, result)
    }
}
