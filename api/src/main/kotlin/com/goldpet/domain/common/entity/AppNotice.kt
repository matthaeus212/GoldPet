package com.goldpet.domain.common.entity

import jakarta.persistence.*
import java.time.LocalDateTime

enum class AppNoticeType {
    POPUP_MODAL, MAINTENANCE, EVENT_BANNER, NOTICE
}

@Entity
@Table(name = "app_notices")
class AppNotice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var type: AppNoticeType,

    @Column(nullable = false, length = 255)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var content: String? = null,

    @Column(name = "image_urls", columnDefinition = "TEXT")
    var imageUrls: String? = null,

    @Column(name = "link_url", length = 500)
    var linkUrl: String? = null,

    @Column(name = "target_screen", length = 100)
    var targetScreen: String? = null,

    @Column(nullable = false)
    var priority: Int = 0,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "is_dismissible", nullable = false)
    var isDismissible: Boolean = true,

    @Column(name = "start_at", nullable = false)
    var startAt: LocalDateTime,

    @Column(name = "end_at")
    var endAt: LocalDateTime? = null,

    @Column(name = "created_by")
    var createdBy: Long? = null
) : BaseTimeEntity()
