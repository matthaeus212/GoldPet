package com.goldpet.domain.admin.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "banners")
class Banner(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var title: String,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    var link: String? = null,

    @Column(name = "is_active")
    var isActive: Boolean = true,

    @Column(name = "display_order")
    var displayOrder: Int = 0,

    @Column(name = "start_date")
    var startDate: LocalDateTime? = null,

    @Column(name = "end_date")
    var endDate: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "placement", nullable = false, length = 20)
    var placement: BannerPlacement = BannerPlacement.HOME
) : BaseTimeEntity()
