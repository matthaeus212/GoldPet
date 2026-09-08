package com.goldpet.domain.user.entity

import com.goldpet.config.crypto.EncryptionConverter
import com.goldpet.config.crypto.LocalDateEncryptionConverter
import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import org.locationtech.jts.geom.Point

@Entity
@Table(name = "users")
class User(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
        @Convert(converter = EncryptionConverter::class)
        @Column(unique = false) // Removed unique constraint as encryption is random IV
        var email: String?,
        @Column(name = "email_hash", unique = true)
        var emailHash: String? = null,
        var oauthProvider: String,
        var oauthId: String,
        var username: String?,
        var password: String?,
        var nickname: String?,
        @Convert(converter = EncryptionConverter::class) var name: String?,
        @Convert(converter = LocalDateEncryptionConverter::class)
        var birthDate: LocalDate?, // "YYYY-MM-DD" → AES-256-GCM encrypted
        @Convert(converter = EncryptionConverter::class) var phoneNumber: String?,
        var gender: String?,
        var birthYear: Int?,
        var mainLocationText: String?,
        @Column(columnDefinition = "geometry(Point, 4326)") var mainLocationGeom: Point?,
        var profileImageUrl: String?,
        var goldBalance: Int = 0,
        var isActive: Boolean = true,
        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        var status: UserStatus = UserStatus.ACTIVE,
        /** 휴면 전환 판정 기준. 로그인 성공 때마다 갱신된다(V89). */
        @Column(name = "last_login_at") var lastLoginAt: LocalDateTime? = null,
        /** DORMANT 로 전환된 시각. 해제 시 null 로 되돌린다(V89). */
        @Column(name = "dormant_at") var dormantAt: LocalDateTime? = null,
        var mbti: String? = null,
        @Column(columnDefinition = "TEXT") var intro: String? = null,
        @Column(name = "has_pet", nullable = false) var hasPet: Boolean = false,
        @Column(name = "is_notification_enabled", nullable = false)
        var isNotificationEnabled: Boolean = true,
        @Column(name = "is_chat_alert_enabled", nullable = false)
        var isChatAlertEnabled: Boolean = true,
        @Column(name = "is_community_alert_enabled", nullable = false)
        var isCommunityAlertEnabled: Boolean = true,
        @Column(name = "is_marketing_alert_enabled", nullable = false)
        var isMarketingAlertEnabled: Boolean = true,
        @Column(name = "is_reengagement_alert_enabled", nullable = false)
        var isReengagementAlertEnabled: Boolean = true,
        @Column(name = "is_location_sharing_enabled", nullable = false)
        var isLocationSharingEnabled: Boolean = true,
        @Column(name = "is_profile_public", nullable = false) var isProfilePublic: Boolean = true,
        @Column(name = "fcm_token") var fcmToken: String? = null,
        @Column(name = "profile_locked_at") var profileLockedAt: LocalDateTime? = null,
        // 온보딩/추가가입 완료의 단일 기준. PII 필드(name/nickname/phone)와 무관.
        @Column(name = "signup_completed_at") var signupCompletedAt: LocalDateTime? = null,
        @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
        @OrderBy("orderIndex ASC")
        var profileImages: MutableList<UserProfileImage> = mutableListOf(),
        @ManyToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
        @JoinTable(
                name = "user_interests",
                joinColumns = [JoinColumn(name = "user_id")],
                inverseJoinColumns = [JoinColumn(name = "interest_id")]
        )
        var interests: MutableSet<Interest> = mutableSetOf(),
        @ManyToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
        @JoinTable(
                name = "user_hobbies",
                joinColumns = [JoinColumn(name = "user_id")],
                inverseJoinColumns = [JoinColumn(name = "hobby_id")]
        )
        var hobbies: MutableSet<Hobby> = mutableSetOf()
) : BaseTimeEntity()
