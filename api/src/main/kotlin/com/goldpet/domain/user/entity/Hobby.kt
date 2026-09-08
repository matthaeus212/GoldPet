package com.goldpet.domain.user.entity

import jakarta.persistence.*

@Entity
@Table(name = "hobbies")
class Hobby(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val name: String,

    @Column(nullable = false)
    val orderIndex: Int = 0
)
