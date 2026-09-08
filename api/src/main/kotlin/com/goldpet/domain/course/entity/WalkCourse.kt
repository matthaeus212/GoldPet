package com.goldpet.domain.course.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point

@Entity
@Table(name = "walk_courses")
class WalkCourse(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    val author: User,

    @Column(nullable = false, length = 100)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(columnDefinition = "geometry(LineString, 4326)", nullable = false)
    var path: LineString,

    @Column(nullable = false)
    var distanceKm: Double,

    @Column(nullable = false)
    var estimatedMinutes: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var difficulty: CourseDifficulty = CourseDifficulty.MODERATE,

    @Column(nullable = false, length = 100)
    var region: String,

    @Column(columnDefinition = "geometry(Point, 4326)", nullable = false)
    var startLocation: Point,

    var startAddress: String? = null,

    var endAddress: String? = null,

    @Column(length = 500)
    var thumbnailUrl: String? = null,

    @Column(nullable = false)
    var likeCount: Int = 0,

    @Column(nullable = false)
    var commentCount: Int = 0,

    @Column(nullable = false)
    var walkCount: Int = 0,

    @Column(nullable = false)
    var rating: Double = 0.0,

    @Column(nullable = false)
    var ratingCount: Int = 0,

    @Column(nullable = false)
    var isPublished: Boolean = true,

    @Column(name = "origin_walk_id")
    var originWalkId: Long? = null,

    // Must be set via GeocodingService.abbreviateProvince() whenever region is set
    @Column(length = 50)
    var province: String? = null

) : BaseTimeEntity()
