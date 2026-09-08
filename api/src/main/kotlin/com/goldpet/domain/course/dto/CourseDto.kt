package com.goldpet.domain.course.dto

import com.goldpet.domain.course.entity.CourseDifficulty
import com.goldpet.domain.course.entity.CourseSpot
import com.goldpet.domain.course.entity.CourseSpotType
import com.goldpet.domain.course.entity.WalkCourse
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.PrecisionModel
import java.time.LocalDateTime

data class CreateCourseRequest(
    @field:NotBlank(message = "제목을 입력해주세요")
    @field:Size(max = 100, message = "제목은 100자 이내로 입력해주세요")
    val title: String,

    val description: String?,

    @field:Size(min = 2, message = "경로는 최소 2개 이상의 좌표가 필요합니다")
    val path: List<List<Double>>, // [[lat, lng], ...]

    @field:Positive(message = "거리는 0보다 커야 합니다")
    val distanceKm: Double,

    @field:Positive(message = "예상 소요 시간은 0보다 커야 합니다")
    val estimatedMinutes: Int,

    val difficulty: CourseDifficulty = CourseDifficulty.MODERATE,

    @field:Valid val spots: List<CourseSpotDto> = emptyList(),

    val originWalkId: Long? = null
) {
    fun toLineString(): LineString {
        require(path.all { it.size >= 2 }) { "Each path point must have at least 2 coordinates" }
        return GeometryFactory(PrecisionModel(), 4326)
            .createLineString(path.map { Coordinate(it[1], it[0]) }.toTypedArray())
    }
}

data class UpdateCourseRequest(
    @field:NotBlank(message = "제목을 입력해주세요")
    @field:Size(max = 100, message = "제목은 100자 이내로 입력해주세요")
    val title: String,

    val description: String?,

    @field:Size(min = 2, message = "경로는 최소 2개 이상의 좌표가 필요합니다")
    val path: List<List<Double>>, // [[lat, lng], ...]

    @field:Positive(message = "거리는 0보다 커야 합니다")
    val distanceKm: Double,

    @field:Positive(message = "예상 소요 시간은 0보다 커야 합니다")
    val estimatedMinutes: Int,

    val difficulty: CourseDifficulty,

    @field:Valid val spots: List<CourseSpotDto> = emptyList()
) {
    fun toLineString(): LineString {
        require(path.all { it.size >= 2 }) { "Each path point must have at least 2 coordinates" }
        return GeometryFactory(PrecisionModel(), 4326)
            .createLineString(path.map { Coordinate(it[1], it[0]) }.toTypedArray())
    }
}

data class CourseSpotDto(
    val id: Long? = null,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val latitude: Double,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val longitude: Double,
    val type: CourseSpotType,
    @field:Size(max = 100)
    val name: String? = null,
    @field:Size(max = 500)
    val description: String? = null,
    @field:Size(max = 500)
    val imageUrl: String? = null,
    @field:Min(0)
    val orderIndex: Int
)

data class CourseSearchRequest(
    val minLat: Double? = null,
    val maxLat: Double? = null,
    val minLng: Double? = null,
    val maxLng: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val radiusMeters: Double = 2000.0,
    val difficulty: CourseDifficulty? = null,
    @field:Min(1) @field:Max(120) val maxMinutes: Int? = null,
    val region: String? = null,
    val page: Int = 0,
    val size: Int = 20
)

data class CourseListResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val difficulty: CourseDifficulty,
    val region: String,
    val startAddress: String?,
    val thumbnailUrl: String?,
    val likeCount: Int,
    val commentCount: Int,
    val walkCount: Int,
    val rating: Double,
    val ratingCount: Int,
    val authorNickname: String,
    val authorProfileImageUrl: String?,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(course: WalkCourse) = CourseListResponse(
            id = course.id,
            title = course.title,
            description = course.description,
            distanceKm = course.distanceKm,
            estimatedMinutes = course.estimatedMinutes,
            difficulty = course.difficulty,
            region = course.region,
            startAddress = course.startAddress,
            thumbnailUrl = course.thumbnailUrl,
            likeCount = course.likeCount,
            commentCount = course.commentCount,
            walkCount = course.walkCount,
            rating = course.rating,
            ratingCount = course.ratingCount,
            authorNickname = course.author.nickname ?: "",
            authorProfileImageUrl = course.author.profileImageUrl,
            createdAt = course.createdAt
        )
    }
}

data class CourseResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val path: List<List<Double>>, // [[lat, lng], ...]
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val difficulty: CourseDifficulty,
    val region: String,
    val province: String?,
    val startAddress: String?,
    val endAddress: String?,
    val thumbnailUrl: String?,
    val likeCount: Int,
    val commentCount: Int,
    val walkCount: Int,
    val rating: Double,
    val ratingCount: Int,
    val isPublished: Boolean,
    val originWalkId: Long?,
    val authorId: Long,
    val authorNickname: String,
    val authorProfileImageUrl: String?,
    val spots: List<CourseSpotResponse>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(course: WalkCourse, spots: List<CourseSpot> = emptyList()) = CourseResponse(
            id = course.id,
            title = course.title,
            description = course.description,
            path = course.path.coordinates.map { listOf(it.y, it.x) },
            distanceKm = course.distanceKm,
            estimatedMinutes = course.estimatedMinutes,
            difficulty = course.difficulty,
            region = course.region,
            province = course.province,
            startAddress = course.startAddress,
            endAddress = course.endAddress,
            thumbnailUrl = course.thumbnailUrl,
            likeCount = course.likeCount,
            commentCount = course.commentCount,
            walkCount = course.walkCount,
            rating = course.rating,
            ratingCount = course.ratingCount,
            isPublished = course.isPublished,
            originWalkId = course.originWalkId,
            authorId = course.author.id,
            authorNickname = course.author.nickname ?: "",
            authorProfileImageUrl = course.author.profileImageUrl,
            spots = spots.map { CourseSpotResponse.from(it) },
            createdAt = course.createdAt,
            updatedAt = course.updatedAt
        )
    }
}

data class CourseSpotResponse(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val type: CourseSpotType,
    val name: String?,
    val description: String?,
    val imageUrl: String?,
    val orderIndex: Int
) {
    companion object {
        fun from(spot: CourseSpot) = CourseSpotResponse(
            id = spot.id,
            latitude = spot.location.y,
            longitude = spot.location.x,
            type = spot.type,
            name = spot.name,
            description = spot.description,
            imageUrl = spot.imageUrl,
            orderIndex = spot.orderIndex
        )
    }
}
