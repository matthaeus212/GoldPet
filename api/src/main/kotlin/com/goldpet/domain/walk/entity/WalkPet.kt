package com.goldpet.domain.walk.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.pet.entity.Pet
import jakarta.persistence.*
import java.io.Serializable

@Entity
@Table(name = "walk_pets")
@IdClass(WalkPetId::class)
class WalkPet(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "walk_id", nullable = false)
    val walk: Walk,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    val pet: Pet
) : BaseTimeEntity()

data class WalkPetId(
    val walk: Long = 0,
    val pet: Long = 0
) : Serializable
