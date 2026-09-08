package com.goldpet.domain.course.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*
import org.locationtech.jts.geom.Point

@Entity
@Table(name = "course_spots")
class CourseSpot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: WalkCourse,

    @Column(columnDefinition = "geometry(Point, 4326)", nullable = false)
    var location: Point,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var type: CourseSpotType,

    @Column(length = 100)
    var name: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(length = 500)
    var imageUrl: String? = null,

    @Column(nullable = false)
    var orderIndex: Int

) : BaseTimeEntity()
