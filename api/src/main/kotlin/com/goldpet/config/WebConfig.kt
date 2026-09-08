package com.goldpet.config

import com.goldpet.config.security.AdminAuditInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.ShallowEtagHeaderFilter
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class WebConfig(
    @Value("\${file.upload-dir:uploads}") private val uploadDir: String,
    private val adminAuditInterceptor: AdminAuditInterceptor
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // 디렉토리가 아직 존재하지 않으면 toUri() 결과에 trailing slash 가 붙지 않아
        // ResourceHandlerUtils 가 WARN 을 남긴다 → 직접 trailing slash 보정.
        val uploadPath = Paths.get(uploadDir).toAbsolutePath().toUri().toString()
            .trimEnd('/') + "/"
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(uploadPath)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(adminAuditInterceptor)
            .addPathPatterns("/api/v1/admin/**")
    }

    /**
     * Sprint 4 BLOCKER #12 — 골드 잔액 multi-device polling 효율화.
     *
     * `GET /api/v1/gold/balance` 응답에 ETag (response body byte hash) 자동 부여.
     * 클라이언트가 `If-None-Match` 헤더로 재요청 시 body 동일하면 304 Not Modified 응답.
     * Multi-device polling 3s 시나리오에서 잔액 변경 없을 경우 RDS read 회피 불가하지만
     * 네트워크/직렬화 비용 절감 (응답 byte 0 + Sentry/Grafana 측정 가능).
     *
     * 적용 범위: `/api/v1/gold` 하위 경로 패턴만 — 다른 endpoint 영향 격리 (안전 우선).
     */
    @Bean
    fun goldEtagFilter(): FilterRegistrationBean<ShallowEtagHeaderFilter> {
        val bean = FilterRegistrationBean(ShallowEtagHeaderFilter())
        bean.addUrlPatterns("/api/v1/gold/*")
        bean.setName("goldEtagFilter")
        return bean
    }

    /**
     * 공개 배너 `GET /api/v1/banners` 응답에 ETag(response body byte hash) 자동 부여.
     * `If-None-Match` 일치 시 304 Not Modified. gold 필터는 gold 경로 전용이라
     * banners 엔 미적용 → 별도 등록 필요. Cache-Control: public, max-age=60 과 병용.
     */
    @Bean
    fun bannerEtagFilter(): FilterRegistrationBean<ShallowEtagHeaderFilter> {
        val bean = FilterRegistrationBean(ShallowEtagHeaderFilter())
        bean.addUrlPatterns("/api/v1/banners")
        bean.setName("bannerEtagFilter")
        return bean
    }
}
