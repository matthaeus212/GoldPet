package com.goldpet.config.security

import com.goldpet.common.ratelimit.UserProfileRateLimitFilter
import com.goldpet.config.jwt.JwtAuthenticationFilter
import com.goldpet.service.oauth2.CustomOAuth2UserService
import com.goldpet.service.oauth2.OAuth2AuthenticationFailureHandler
import com.goldpet.service.oauth2.OAuth2AuthenticationSuccessHandler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties::class)
class SecurityConfig(
        private val customOAuth2UserService: CustomOAuth2UserService,
        private val oAuth2AuthenticationSuccessHandler: OAuth2AuthenticationSuccessHandler,
        private val oAuth2AuthenticationFailureHandler: OAuth2AuthenticationFailureHandler,
        private val jwtAuthenticationFilter: JwtAuthenticationFilter,
        private val adminAuditingAccessDeniedHandler: AdminAuditingAccessDeniedHandler,
        private val corsProperties: CorsProperties,
        // Optional — bean 은 `app.rate-limit.user-profile.enabled=true` 일 때만 생성됨
        // (codegen/test profile 은 `false` 로 skip 하여 Redis 의존성 제거).
        // Kotlin default-null 은 `@Configuration` CGLIB proxy 의 no-arg ctor 합성 실패를
        //유발하므로 `ObjectProvider` 로 우회. `ifAvailable` 은 bean 부재 시 null 반환.
        private val userProfileRateLimitFilterProvider: ObjectProvider<UserProfileRateLimitFilter>,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
                .csrf { it.disable() } // Disable CSRF for API
                .cors { it.configurationSource(corsConfigurationSource()) } // Enable CORS
                .sessionManagement { session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                } // Use session if required (for OAuth2)
                .httpBasic { it.disable() } // Disable basic auth
                .formLogin { it.disable() } // Disable form login
                .authorizeHttpRequests { auth ->
                    auth.requestMatchers("/api/v1/auth/**")
                            .permitAll() // Allow signup & login
                            .requestMatchers("/")
                            .permitAll() // Allow root endpoint (Health check)
                            // SEC-006/EXT-CDX-010: actuator 노출 축소. health/info 만 공개하고
                            // prometheus 등 나머지는 인증 뒤로(anyRequest authenticated). 외부는
                            // nginx 가 이미 차단(OPS-004) — 이건 심층방어.
                            .requestMatchers("/actuator/health", "/actuator/info")
                            .permitAll()
                            .requestMatchers(
                                    "/api/v1/admin/auth/login",
                                    "/api/v1/admin/auth/verify-2fa"
                            )
                            .permitAll() // Allow Admin login & verify
                            .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/**")
                            .authenticated() // Secure other admin auth endpoints (setup, confirm,
                            // remove)
                            .requestMatchers(HttpMethod.GET, "/api/v1/walks/ranking")
                            .permitAll() // Allow public walk ranking
                            .requestMatchers(HttpMethod.GET, "/api/v1/banners")
                            .permitAll() // Allow public banner listing (app/web, no auth)
                            .requestMatchers(HttpMethod.GET, "/api/v1/ai-profile/loading-tips")
                            .permitAll() // Allow public loading tips
                            .requestMatchers("/oauth2/**")
                            .permitAll() // Allow access to OAuth2 endpoints
                            .requestMatchers("/cdn-cgi/**")
                            .permitAll() // Allow Cloudflare RUM
                            // community-author-profile-gallery §4-1: 공개 프로필/작성자 게시글은
                            // enumeration 방어를 위해 JWT 필수. `/api/v1/users/**` 은 `anyRequest()`
                            // 기본 authenticated 에 포함되나, 명시성/문서화 목적으로 개별 matcher 를 둔다.
                            .requestMatchers(HttpMethod.GET, "/api/v1/users/*/public-profile")
                            .authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/v1/users/*/community/posts")
                            .authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/v1/users/*/walks/photos")
                            .authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/v1/community/**")
                            .permitAll() // Allow community read access
                            .requestMatchers(HttpMethod.GET, "/api/v1/courses/**")
                            .permitAll() // Allow course read access
                            .requestMatchers("/ws/**")
                            .permitAll() // Allow WebSocket connection
                            .requestMatchers("/uploads/**")
                            .permitAll() // Allow static file access
                            .requestMatchers("/api/v1/settings/**")
                            .permitAll() // Allow public settings
                            .requestMatchers("/api/v1/app/**")
                            .permitAll() // Allow app version check (no auth required)
                            .requestMatchers("/api/v1/client-logs/**")
                            .permitAll() // Allow client-side runtime error reporting
                            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                            .permitAll() // Allow Swagger
                            .requestMatchers("/error")
                            .permitAll() // Allow Error dispatch
                            .requestMatchers("/api/v1/admin/migration/**")
                            .hasAnyRole("SUPER_ADMIN") // Restrict PII key rotation endpoints
                            .anyRequest()
                            .authenticated()
                }
                .oauth2Login { oauth2Login ->
                    oauth2Login
                            .userInfoEndpoint { userInfo ->
                                userInfo.userService(customOAuth2UserService)
                            } // Use custom OAuth2 user service
                            .successHandler(
                                    oAuth2AuthenticationSuccessHandler
                            ) // Use custom success handler
                            .failureHandler(
                                    oAuth2AuthenticationFailureHandler
                            ) // Use custom failure handler
                }
                .exceptionHandling {
                    it.authenticationEntryPoint(
                            org.springframework.security.web.authentication.HttpStatusEntryPoint(
                                    org.springframework.http.HttpStatus.UNAUTHORIZED
                            )
                    )
                    it.accessDeniedHandler(adminAuditingAccessDeniedHandler)
                }
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        org.springframework.security.web.authentication
                                        .UsernamePasswordAuthenticationFilter::class
                                .java
                ) // Add JWT filter
        // Rate-limit filter runs AFTER JWT authentication so that `SecurityContextHolder`
        // is populated and we can derive per-viewer buckets. Protected paths are matched
        // inside the filter via AntPathMatcher. codegen/test profile 에서는 bean 부재로 skip.
        userProfileRateLimitFilterProvider.ifAvailable?.let { filter ->
            http.addFilterAfter(filter, JwtAuthenticationFilter::class.java)
        }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): org.springframework.web.cors.CorsConfigurationSource {
        val configuration = org.springframework.web.cors.CorsConfiguration()
        // CORS-DEFAULT (W1a): 미설정 시 fail-fast. 이전의 `:*` 기본값은 dev/prod 의 YAML 리스트
        // 미바인딩을 은폐해 라이브를 사실상 CORS 무제한으로 두었다. dev/prod/local/test yml 은
        // 명시적 origin 리스트를, codegen yml 만 `"*"` 를 제공한다.
        val origins = corsProperties.allowedOrigins.map { it.trim() }.filter { it.isNotEmpty() }
        check(origins.isNotEmpty()) {
            "app.cors.allowed-origins 가 설정되지 않았습니다. 활성 프로파일 yml 에 명시적 origin 목록을 지정하세요."
        }
        // allowedOriginPatterns 는 credentials 동반 시에도 codegen 의 `*` 패턴을 허용(스펙 위반 예외 없음).
        // allowedOrigins(=`*`)+allowCredentials 조합만 스펙상 금지되므로 patterns 를 유지한다.
        configuration.allowedOriginPatterns = origins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        val source = org.springframework.web.cors.UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}
