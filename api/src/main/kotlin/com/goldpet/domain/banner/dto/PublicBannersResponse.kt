package com.goldpet.domain.banner.dto

/**
 * 공개 배너 통합 응답 — named DTO.
 *
 * placement enum-key `Map` 대신 명시 4필드를 쓴다(openapi-typescript 가 enum-key map 을
 * `{[key:string]:...}` 로 렌더해 타입안전을 잃기 때문). 빈 placement 는 빈 리스트.
 */
data class PublicBannersResponse(
    val home: List<PublicBannerResponse>,
    val chat: List<PublicBannerResponse>,
    val walk: List<PublicBannerResponse>,
    val community: List<PublicBannerResponse>
)
