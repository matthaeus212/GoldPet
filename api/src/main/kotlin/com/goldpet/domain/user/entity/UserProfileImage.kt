package com.goldpet.domain.user.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*

@Entity
@Table(name = "user_profile_images")
class UserProfileImage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    var imageUrl: String,

    @Column(nullable = false)
    var orderIndex: Int
) : BaseTimeEntity()
