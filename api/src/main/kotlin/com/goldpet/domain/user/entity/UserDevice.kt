package com.goldpet.domain.user.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user_devices")
class UserDevice(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "device_id", nullable = false)
    val deviceId: String,

    @Column(name = "fcm_token", length = 500)
    var fcmToken: String? = null,

    @Column(name = "device_type", nullable = false)
    @Enumerated(EnumType.STRING)
    var deviceType: DeviceType = DeviceType.UNKNOWN,

    @Column(name = "device_name")
    var deviceName: String? = null,

    @Column(name = "app_version")
    var appVersion: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "last_login_at", nullable = false)
    var lastLoginAt: LocalDateTime = LocalDateTime.now()
) : BaseTimeEntity()

enum class DeviceType {
    ANDROID, IOS, WEB, UNKNOWN
}
