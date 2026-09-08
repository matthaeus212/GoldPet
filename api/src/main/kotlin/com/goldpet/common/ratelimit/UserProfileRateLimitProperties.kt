package com.goldpet.common.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * community-author-profile-gallery §4-1 "권한/보안 Enumeration 방지" + Plan §7 Day 0.
 *
 * `GET /api/v1/users/{id}/public-profile` 및 `GET /api/v1/users/{id}/community/posts` 의
 * per-viewer / per-IP rate-limit. Bucket4j + Lettuce(Redis) 로 분산 버킷 관리.
 *
 * application.yml 예:
 * ```
 * app:
 *   rate-limit:
 *     user-profile:
 *       enabled: true
 *       viewer-per-minute: 60
 *       ip-per-minute: 120
 * ```
 *
 * integration test / CI 에서는 `application-test.yml` 에 `enabled: false` 로 비활성.
 */
@ConfigurationProperties("app.rate-limit.user-profile")
data class UserProfileRateLimitProperties(
    val enabled: Boolean = true,
    val viewerPerMinute: Long = 60,
    val ipPerMinute: Long = 120,
)
