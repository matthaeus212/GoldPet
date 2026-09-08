package com.goldpet.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI {
        val jwtSchemeName = "jwtAuth"
        val securityRequirement = SecurityRequirement().addList(jwtSchemeName)
        val components = Components()
            .addSecuritySchemes(
                jwtSchemeName,
                SecurityScheme()
                    .name(jwtSchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            )

        // Pin the server URL so codegen 프로파일의 포트 오버라이드(18081 등) 가
        // committed spec 에 새어 들어오지 않음. 생성 결과는 항상 운영 포트(8081) 고정.
        val servers = listOf(Server().url("http://localhost:8081"))

        return OpenAPI()
            .info(
                Info().title("GoldPet API")
                    .description("GoldPet Backend API Documentation")
                    .version("1.0.0")
            )
            .servers(servers)
            .addSecurityItem(securityRequirement)
            .components(components)
    }
}
