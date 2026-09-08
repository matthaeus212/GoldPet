package com.goldpet.domain.walk.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*
import org.locationtech.jts.geom.Point
import java.time.LocalDateTime

@Entity
@Table(name = "walk_spots")
class WalkSpot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "walk_id", nullable = false)
    val walk: Walk,

    @Column(columnDefinition = "geometry(Point, 4326)", nullable = false)
    val location: Point,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: WalkSpotType,

    @Column(nullable = false)
    val timestamp: LocalDateTime,

    var imageUrl: String? = null,

    @Column(columnDefinition = "TEXT")
    var note: String? = null,

    @Column(name = "image_key_viewer")
    var imageKeyViewer: String? = null,

    // 600px medium 변형 키(iOS WKWebView 메모리 완화용 스와이프 표시). 미생성 사진은 null —
    // 파생하지 않고 저장한다(파생 시 객체 없는 구 사진에서 404 → 큰 사진 blank). null 이면 클라가
    // `medium ?? viewer` 폴백으로 viewer 를 쓰고, 백필 워커가 생성하며 이 값을 채운다.
    @Column(name = "image_key_medium")
    var imageKeyMedium: String? = null,

    @Column(name = "image_key_thumb")
    var imageKeyThumb: String? = null,

    @Column(nullable = false)
    var hiddenFromPublic: Boolean = false

) : BaseTimeEntity()

enum class WalkSpotType {
    PEE, POOP, PHOTO, PROBLEM, OTHER
}
