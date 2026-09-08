package com.goldpet.domain.banner.dto

/**
 * 공개 배너 응답 아이템 — 비인증 `GET /api/v1/banners` 전용.
 *
 * 어드민 [com.goldpet.domain.admin.dto.BannerResponse] 재사용 금지:
 * isActive/displayOrder/startDate/endDate 등 내부 운영필드를 공개 API로 누설하지 않도록
 * 노출 필드를 7개로 최소화한다.
 */
data class PublicBannerResponse(
    val id: Long,
    val title: String,
    val imageUrl: String?,
    val imageUrlThumbnail: String?,
    val imageUrlViewer: String?,
    val link: String?,
    val placement: String
)
