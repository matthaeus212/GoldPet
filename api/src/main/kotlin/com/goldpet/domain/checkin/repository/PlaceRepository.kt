package com.goldpet.domain.checkin.repository

import com.goldpet.domain.checkin.entity.Place
import com.goldpet.domain.checkin.entity.PlaceCategory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PlaceRepository : JpaRepository<Place, Long> {
    // Admin-facing (deleted_at 무시 OK)
    fun findAllByDeletedAtIsNull(): List<Place>

    fun findByIdAndDeletedAtIsNull(id: Long): Place?

    // User-facing (soft-delete 필터 필수)
    fun findByCategoryAndDeletedAtIsNull(category: PlaceCategory, pageable: Pageable): Page<Place>

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Place>

    fun findByNameContainingIgnoreCaseAndDeletedAtIsNull(name: String, pageable: Pageable): Page<Place>

    @Query(value = """
        SELECT * FROM places p
        WHERE ST_DWithin(p.location_geom, ST_SetSRID(ST_Point(:lng, :lat), 4326)::geography, :radiusMeters)
          AND p.deleted_at IS NULL
        ORDER BY ST_Distance(p.location_geom, ST_SetSRID(ST_Point(:lng, :lat), 4326)::geography)
    """, nativeQuery = true)
    fun findNearbyPlaces(
        @Param("lat") latitude: Double,
        @Param("lng") longitude: Double,
        @Param("radiusMeters") radiusMeters: Double
    ): List<Place>
}
