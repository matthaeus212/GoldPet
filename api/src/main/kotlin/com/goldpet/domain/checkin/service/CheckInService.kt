package com.goldpet.domain.checkin.service

import com.goldpet.domain.checkin.dto.*
import com.goldpet.domain.checkin.entity.CheckIn
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.checkin.entity.Place
import com.goldpet.domain.checkin.entity.PlaceCategory
import com.goldpet.domain.checkin.repository.CheckInRepository
import com.goldpet.domain.checkin.repository.PlaceRepository
import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.gamification.service.BadgeAwardService
import com.goldpet.domain.user.repository.UserRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class CheckInService(
    private val placeRepository: PlaceRepository,
    private val checkInRepository: CheckInRepository,
    private val userRepository: UserRepository,
    private val badgeAwardService: BadgeAwardService
) {
    private val log = LoggerFactory.getLogger(CheckInService::class.java)
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    // Place methods
    fun getNearbyPlaces(request: NearbyPlaceRequest): List<PlaceResponse> {
        return placeRepository.findNearbyPlaces(request.latitude, request.longitude, request.radiusMeters)
            .map { PlaceResponse.from(it) }
    }

    fun getPlaces(category: PlaceCategory?, pageable: Pageable): Page<PlaceResponse> {
        val places = if (category != null) {
            placeRepository.findByCategoryAndDeletedAtIsNull(category, pageable)
        } else {
            placeRepository.findAllByDeletedAtIsNull(pageable)
        }
        return places.map { PlaceResponse.from(it) }
    }

    fun getPlace(placeId: Long): PlaceResponse {
        val place = placeRepository.findByIdAndDeletedAtIsNull(placeId)
            ?: throw NotFoundException("Place not found")
        return PlaceResponse.from(place)
    }

    fun searchPlaces(name: String, pageable: Pageable): Page<PlaceResponse> {
        return placeRepository.findByNameContainingIgnoreCaseAndDeletedAtIsNull(name, pageable)
            .map { PlaceResponse.from(it) }
    }

    @Transactional
    fun createPlace(request: CreatePlaceRequest): PlaceResponse {
        val point = geometryFactory.createPoint(Coordinate(request.longitude, request.latitude))
        
        val place = Place(
            name = request.name,
            category = request.category,
            address = request.address,
            locationGeom = point,
            description = request.description
        )
        
        val saved = placeRepository.save(place)
        return PlaceResponse.from(saved)
    }

    // CheckIn methods
    fun getMyCheckIns(userId: Long, pageable: Pageable): Page<CheckInResponse> {
        return checkInRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map { CheckInResponse.from(it) }
    }

    fun getPlaceCheckIns(placeId: Long, pageable: Pageable): Page<CheckInResponse> {
        return checkInRepository.findAllByPlaceIdOrderByCreatedAtDesc(placeId, pageable)
            .map { CheckInResponse.from(it) }
    }

    @Transactional
    fun checkIn(userId: Long, request: CreateCheckInRequest): CheckInResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found") }
        val place = placeRepository.findByIdAndDeletedAtIsNull(request.placeId)
            ?: throw NotFoundException("Place not found")

        // 같은 장소에 1시간 내 중복 체크인 방지
        val oneHourAgo = LocalDateTime.now().minusHours(1)
        if (checkInRepository.existsByUserIdAndPlaceIdAndCreatedAtAfter(userId, request.placeId, oneHourAgo)) {
            throw ConflictException("Already checked in recently")
        }
        
        val checkIn = CheckIn(
            user = user,
            place = place,
            photoUrl = request.photoUrl,
            memo = request.memo
        )
        
        place.checkinCount += 1
        placeRepository.save(place)
        
        val saved = checkInRepository.save(checkIn)

        try {
            badgeAwardService.checkAndAwardBadges(userId, BadgeConditionType.CHECK_IN)
        } catch (e: Exception) {
            log.warn("Badge check failed for userId={}", userId, e)
        }

        return CheckInResponse.from(saved)
    }

    fun getCheckInCount(userId: Long): Long {
        val thirtyDaysAgo = LocalDateTime.now().minusDays(30)
        return checkInRepository.countByUserIdAndCreatedAtAfter(userId, thirtyDaysAgo)
    }
}
