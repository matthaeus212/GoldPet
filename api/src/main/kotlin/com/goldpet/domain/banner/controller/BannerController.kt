package com.goldpet.domain.banner.controller

import com.goldpet.domain.banner.dto.PublicBannersResponse
import com.goldpet.domain.banner.service.PublicBannerService
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 공개 배너 조회 — 비인증 permitAll(SecurityConfig). 파라미터 없이 1회 호출로 전 placement 반환.
 *
 * 캐싱 정책:
 * - 서버: 응답 데이터는 Redis("banners:public")에 캐시되고 admin 의 배너 생성/수정/삭제가
 *   @CacheEvict 로 즉시 무효화 → 변경 직후 조회는 DB 재조회 후 재캐시.
 * - 클라이언트: `Cache-Control: no-cache` 로 매 요청 재검증을 강제한다(max-age 캐시 금지 —
 *   admin 비활성화가 60초간 클라 캐시에 묶여 계속 노출되던 문제 해결). 미변경 시 WebConfig 의
 *   ShallowEtagHeaderFilter 가 ETag 비교로 304(본문 미전송)를 돌려주어 비용은 낮게 유지.
 */
@RestController
@RequestMapping("/api/v1/banners")
class BannerController(
    private val publicBannerService: PublicBannerService
) {
    @GetMapping
    fun getBanners(): ResponseEntity<PublicBannersResponse> {
        val body = publicBannerService.getPublicBanners()
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .body(body)
    }
}
