package com.goldpet.domain.community.entity

import jakarta.persistence.*

@Entity
@Table(name = "community_categories")
class CommunityCategory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val code: String,

    @Column(nullable = false)
    val name: String
)
