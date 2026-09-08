package com.goldpet.domain.checkin.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*

@Entity
@Table(name = "check_ins")
class CheckIn(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    val place: Place,

    @Column(name = "photo_url")
    val photoUrl: String? = null,

    @Column(name = "memo")
    val memo: String? = null

) : BaseTimeEntity()
