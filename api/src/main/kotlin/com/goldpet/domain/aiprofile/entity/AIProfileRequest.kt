package com.goldpet.domain.aiprofile.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.pet.entity.Pet
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*

enum class AIRequestType {
    IMAGE,      // AI 이미지 생성
    VIDEO,      // AI 비디오 생성
    AVATAR      // AI 아바타 생성
}

enum class AIRequestStatus {
    PENDING,    // 대기중
    PROCESSING, // 처리중
    COMPLETED,  // 완료
    FAILED,     // 실패
    REFUNDED    // 환불됨
}

@Entity
@Table(name = "ai_profile_requests")
class AIProfileRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    val pet: Pet? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: AIRequestType,

    @Column(name = "source_image_url", nullable = false)
    val sourceImageUrl: String,

    @Column(name = "style_prompt")
    val stylePrompt: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AIRequestStatus = AIRequestStatus.PENDING,

    @Column(name = "result_url")
    var resultUrl: String? = null,

    @Column(name = "error_message")
    var errorMessage: String? = null,

    @Column(name = "gold_cost", nullable = false)
    val goldCost: Int = 10,

    @Column(name = "pet_type")
    val petType: String? = null,

    @Column(name = "leonardo_generation_id")
    var leonardoGenerationId: String? = null,

    @Column(name = "leonardo_image_id")
    var leonardoImageId: String? = null

) : BaseTimeEntity()
