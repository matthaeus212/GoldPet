package com.goldpet.domain.pet.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "pet_species")
class PetSpecies(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(unique = true, nullable = false)
    val code: String,

    @Column(nullable = false)
    val name: String,

    val description: String?,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
