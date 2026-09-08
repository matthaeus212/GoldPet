// 산책사진 presigned URL 을 키 단위로 고정해 브라우저 이미지 캐시가 적중하게 하는 애플리케이션 스코프 캐시
package com.goldpet.domain.walk.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 같은 S3 키를 응답마다 새로 서명하면 `X-Amz-Date`/`X-Amz-Signature` 가 달라져 URL 이 매번 바뀐다.
 * 브라우저는 이를 서로 다른 리소스로 보고 재다운로드·재디코딩하며, WKWebView 는 이전 URL 의 디코딩
 * 비트맵을 따로 들고 있어 같은 사진이 메모리에 여러 벌 쌓인다. 피드 새로고침 1회 = 전 사진 재디코딩.
 * (라이브 s3 access log: 사진 75장인데 동일 썸네일 1장이 31회 재다운로드, 총 997건.)
 *
 * 키당 URL 을 [WINDOW] 동안 고정해 동일 URL 을 재사용하면 캐시가 적중한다.
 * [WINDOW] 를 [PhotoUrlSigner.PRESIGN_TTL_MINUTES] 보다 짧게 잡아, 창 끝에서 꺼내 쓴 URL 도
 * 최소 (TTL - WINDOW) 만큼은 유효하다 — 만료된 URL 을 캐시가 계속 내보내는 일이 없다.
 */
@Component
class SignedUrlCache {
    private val cache: Cache<String, String> = Caffeine.newBuilder()
        .maximumSize(MAX_ENTRIES)
        .expireAfterWrite(WINDOW)
        .build()

    fun cachedSign(key: String, sign: () -> String): String = cache.get(key) { sign() }!!

    companion object {
        val WINDOW: Duration = Duration.ofMinutes(20)
        const val MAX_ENTRIES = 20_000L
    }
}
