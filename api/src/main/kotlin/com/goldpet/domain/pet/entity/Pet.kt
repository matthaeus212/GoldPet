package com.goldpet.domain.pet.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "pets")
class Pet(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    val owner: User,

    @Column(nullable = false)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "species_id", nullable = false)
    var species: PetSpecies,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "breed_id")
    var breed: PetBreed?,

    var gender: String?,

    var birthDate: LocalDate?,

    var weightKg: Double?,

    var isNeutered: Boolean?,

    var profileImageUrl: String?,

    @Column(columnDefinition = "TEXT")
    var temperamentTags: String?,

    @OneToMany(mappedBy = "pet", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    var profileImages: MutableList<PetProfileImage> = mutableListOf()

) : BaseTimeEntity()
