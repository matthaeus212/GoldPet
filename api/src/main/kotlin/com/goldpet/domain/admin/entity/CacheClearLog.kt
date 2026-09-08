package com.goldpet.domain.admin.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "cache_clear_logs")
class CacheClearLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "cache_name", nullable = false, length = 100)
    val cacheName: String,

    @Column(name = "cleared_by", nullable = false, length = 100)
    val clearedBy: String,

    @Column(name = "cleared_at", nullable = false)
    val clearedAt: LocalDateTime = LocalDateTime.now(),

    @Column(length = 255)
    val reason: String? = null
)
