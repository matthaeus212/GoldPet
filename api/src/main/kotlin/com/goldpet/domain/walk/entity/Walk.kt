package com.goldpet.domain.walk.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import java.time.LocalDateTime

@Entity
@Table(name = "walks")
class Walk(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    var startTime: LocalDateTime,

    @Column(nullable = false)
    var endTime: LocalDateTime,

    @Column(nullable = false)
    var distanceKm: Double,

    @Column(nullable = false)
    var durationSeconds: Long,

    @Column(columnDefinition = "geometry(LineString, 4326)", nullable = false)
    var path: LineString,

    var caloriesBurned: Double?,

    var notes: String?,

    // PERF-005: 산책 목록에서 spots 지연 컬렉션 N+1 방지. 두 컬렉션 모두 bag(List)이라
    // @EntityGraph 동시 fetch join 은 MultipleBagFetchException → per-collection @BatchSize 로
    // 페이지 단위 IN 배치 로딩(전역 default_batch_fetch_size 미사용, Walk 로 국한).
    @OneToMany(mappedBy = "walk", cascade = [CascadeType.ALL], orphanRemoval = true)
    @BatchSize(size = 100)
    var spots: MutableList<WalkSpot> = mutableListOf(),

    var startAddress: String? = null,

    var endAddress: String? = null,

    @Column(columnDefinition = "geometry(Point, 4326)")
    var startLocation: Point? = null,

    @Column(columnDefinition = "geometry(Point, 4326)")
    var endLocation: Point? = null,

    @OneToMany(mappedBy = "walk", cascade = [CascadeType.ALL], orphanRemoval = true)
    @BatchSize(size = 100)
    var walkPets: MutableList<WalkPet> = mutableListOf(),

    @Column(nullable = false)
    var isPublic: Boolean = true,

    @Column(name = "followed_course_id")
    var followedCourseId: Long? = null,

    var province: String? = null

) : BaseTimeEntity()
