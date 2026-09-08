package com.goldpet.domain.pet.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "pet_breeds")
class PetBreed(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "species_id", nullable = false)
    val petSpecies: PetSpecies,

    @Column(nullable = false)
    var name: String,

    var description: String? = null,

    @Column(nullable = true)
    var category: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    var updatedAt: LocalDateTime? = null
) {
    fun update(name: String, category: String?, description: String?) {
        this.name = name
        this.category = category
        this.description = description
        this.updatedAt = LocalDateTime.now()
    }
}
