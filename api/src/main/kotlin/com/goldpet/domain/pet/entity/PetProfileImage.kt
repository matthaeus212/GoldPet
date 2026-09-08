package com.goldpet.domain.pet.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*

@Entity
@Table(name = "pet_profile_images")
class PetProfileImage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    val pet: Pet,

    @Column(nullable = false)
    var imageUrl: String,

    @Column(nullable = false)
    var orderIndex: Int
) : BaseTimeEntity()
