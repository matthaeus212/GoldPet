package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.*
import com.goldpet.domain.checkin.entity.Place
import com.goldpet.domain.checkin.entity.PlaceCategory
import com.goldpet.domain.checkin.repository.CheckInRepository
import com.goldpet.domain.checkin.repository.PlaceRepository
import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.walk.repository.WalkRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Service
class AdminLBSService(
    private val walkRepository: WalkRepository,
    private val placeRepository: PlaceRepository,
    private val checkInRepository: CheckInRepository
) {
    fun getStats(): LBSStatsResponse {
        val today = LocalDate.now().atStartOfDay()

        return LBSStatsResponse(
            totalWalks = walkRepository.count(),
            todayWalks = walkRepository.countByStartTimeAfter(today),
            totalDistance = walkRepository.sumTotalDistanceKm(),
            avgDuration = (walkRepository.avgDurationSeconds() / 60.0).roundToInt(),
            topSpots = checkInRepository.findTopPlaces(5).map { row ->
                TopSpotResponse(
                    id = (row[0] as Number).toLong(),
                    name = row[1] as String,
                    visits = (row[2] as Number).toInt()
                )
            }
        )
    }

    fun getPlaces(): List<PlaceAdminResponse> {
        return placeRepository.findAllByDeletedAtIsNull().map { place ->
            toPlaceAdminResponse(place)
        }
    }

    fun getPlaces(pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<PlaceAdminResponse> {
        return placeRepository.findAllByDeletedAtIsNull(pageable).map { toPlaceAdminResponse(it) }
    }

    @Transactional
    fun createPlace(request: CreatePlaceRequest): PlaceAdminResponse {
        val geometryFactory = GeometryFactory(PrecisionModel(), 4326)
        val point = geometryFactory.createPoint(Coordinate(request.longitude, request.latitude))
        val category = PlaceCategory.entries.find { it.name == request.category.uppercase() }
            ?: throw IllegalArgumentException("유효하지 않은 카테고리: ${request.category}. 가능한 값: ${PlaceCategory.entries.joinToString { it.name }}")
        val place = placeRepository.save(Place(
            name = request.name,
            category = category,
            locationGeom = point
        ))
        return toPlaceAdminResponse(place)
    }

    @Transactional
    fun updatePlace(id: Long, request: UpdatePlaceRequest): PlaceAdminResponse {
        val place = placeRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw NotFoundException("Place not found: $id")

        request.name?.let { place.name = it }
        request.category?.let { cat ->
            place.category = PlaceCategory.entries.find { it.name == cat.uppercase() }
                ?: throw IllegalArgumentException("유효하지 않은 카테고리: $cat. 가능한 값: ${PlaceCategory.entries.joinToString { it.name }}")
        }

        if (request.latitude != null || request.longitude != null) {
            val newLat = request.latitude ?: place.locationGeom.y
            val newLng = request.longitude ?: place.locationGeom.x
            val factory = GeometryFactory(PrecisionModel(), 4326)
            place.locationGeom = factory.createPoint(Coordinate(newLng, newLat))
        }

        return toPlaceAdminResponse(placeRepository.save(place))
    }

    @Transactional
    fun deletePlace(id: Long) {
        val place = placeRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw NotFoundException("Place not found: $id")
        place.deletedAt = LocalDateTime.now()
        placeRepository.save(place)
    }

    private fun toPlaceAdminResponse(place: Place): PlaceAdminResponse {
        return PlaceAdminResponse(
            id = place.id,
            name = place.name,
            category = place.category.name,
            latitude = place.locationGeom.y,
            longitude = place.locationGeom.x,
            visits = place.checkinCount,
            rating = 0.0
        )
    }
}
