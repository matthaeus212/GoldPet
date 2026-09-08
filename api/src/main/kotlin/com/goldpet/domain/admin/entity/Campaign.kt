package com.goldpet.domain.admin.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "campaigns")
class Campaign(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    var name: String,

    var description: String? = null,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDateTime,

    @Column(name = "end_date", nullable = false)
    var endDate: LocalDateTime,

    @Column(nullable = false)
    var status: String = "DRAFT",

    @Column(name = "participant_count")
    var participantCount: Int = 0
) : BaseTimeEntity()
