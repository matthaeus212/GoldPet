// CORS 오리진이 YAML 리스트로 정확히 바인딩되는지(=`*` 폴백이 아닌지) 검증하는 통합 테스트
package com.goldpet.config.security

import com.goldpet.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.cors.CorsConfigurationSource

/**
 * CORS-DEFAULT (W1a) 회귀 테스트.
 *
 * 이전 `@Value("\${app.cors.allowed-origins:*}") String` 은 YAML 리스트를 바인딩하지 못해
 * 항상 `*` 로 폴백했다. `CorsProperties`(@ConfigurationProperties) 로 전환 후 리스트가
 * 정확히 바인딩되고, corsConfigurationSource 가 `*` 가 아닌 명시 origin 을 사용함을 확인한다.
 * 미설정 시 fail-fast 는 컨텍스트 기동 자체가 검증한다(빈 목록이면 startup 실패).
 */
class CorsConfigurationBindingTest : IntegrationTestBase() {

    @Autowired
    lateinit var corsProperties: CorsProperties

    @Autowired
    lateinit var corsConfigurationSource: CorsConfigurationSource

    @Test
    fun `app_cors_allowed-origins YAML 리스트가 정확히 바인딩된다`() {
        assertTrue(corsProperties.allowedOrigins.isNotEmpty(), "origin 목록이 비어있으면 안 된다")
        assertTrue(corsProperties.allowedOrigins.contains("http://localhost:5173"))
        assertFalse(corsProperties.allowedOrigins.contains("*"), "test 프로파일은 `*` 를 쓰지 않는다")
    }

    @Test
    fun `corsConfigurationSource 는 명시 origin 을 적용한다`() {
        val request = MockHttpServletRequest("GET", "/api/v1/chat/rooms")
        val config = corsConfigurationSource.getCorsConfiguration(request)
        val patterns = config?.allowedOriginPatterns ?: emptyList()
        assertTrue(patterns.contains("http://localhost:5173"))
        assertFalse(patterns.contains("*"))
    }
}
