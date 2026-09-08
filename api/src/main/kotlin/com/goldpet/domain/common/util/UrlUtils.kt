package com.goldpet.domain.common.util

/**
 * HTTP → HTTPS 변환 유틸리티.
 * SNS OAuth 프로필 이미지 등 외부 URL이 http://로 들어올 때
 * Android cleartext 차단 정책에 대응하기 위해 사용.
 */
fun String?.toHttps(): String? =
    this?.replaceFirst("http://", "https://")
