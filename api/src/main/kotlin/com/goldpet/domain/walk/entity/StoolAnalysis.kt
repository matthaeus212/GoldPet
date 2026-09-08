package com.goldpet.domain.walk.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.pet.entity.Pet
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "stool_analyses")
class StoolAnalysis(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "walk_spot_id", unique = true)
    val walkSpot: WalkSpot? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id", nullable = false)
    val pet: Pet,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val imageUrl: String,

    var colorScore: Int? = null,
    var consistencyScore: Int? = null,
    var coatingScore: Int? = null,
    var contentsScore: Int? = null,
    var overallScore: Int? = null,

    var colorAssessment: String? = null,
    var consistencyAssessment: String? = null,
    var coatingAssessment: String? = null,
    var contentsAssessment: String? = null,

    @Column(columnDefinition = "TEXT")
    var healthSummary: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var healthTips: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var warnings: String? = null,

    @Column(columnDefinition = "TEXT")
    var aiRawResponse: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AnalysisStatus = AnalysisStatus.PENDING,

    var errorMessage: String? = null,

    var analyzedAt: LocalDateTime? = null

) : BaseTimeEntity()
