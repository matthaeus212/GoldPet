// CORS 허용 오리진 목록을 바인딩하는 설정 프로퍼티 (YAML 리스트/스칼라 모두 지원)
package com.goldpet.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * CORS-DEFAULT (W1a) — `app.cors.allowed-origins` 바인딩.
 *
 * 기존 `@Value("\${app.cors.allowed-origins:*}") String` 은 dev/prod yml 의
 * **YAML 리스트**(`allowed-origins:\n  - https://a`)를 스칼라로 바인딩하지 못해
 * 항상 기본값 `*` 로 폴백했다(= 모든 오리진 허용 + allowCredentials 동반). 즉 라이브가
 * 사실상 CORS 무제한 상태였다.
 *
 * `@ConfigurationProperties` 는 relaxed binding 으로 YAML 리스트를 `List<String>` 으로,
 * codegen 의 스칼라 `"*"` 는 단일 원소 리스트(`["*"]`)로 정확히 바인딩한다.
 * 미설정(`emptyList`) 시 `SecurityConfig.corsConfigurationSource` 가 fail-fast 한다.
 */
@ConfigurationProperties("app.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
