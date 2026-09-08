package com.goldpet.domain.admin.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*

@Entity
@Table(name = "notification_templates")
class NotificationTemplate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "title_template", nullable = false, length = 200)
    var titleTemplate: String,

    @Column(name = "message_template", nullable = false, length = 1000)
    var messageTemplate: String,

    @Column(nullable = false, length = 50)
    var type: String,  // NOTICE, EVENT, MARKETING

    @Column(columnDefinition = "TEXT")
    var variables: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
) : BaseTimeEntity()
