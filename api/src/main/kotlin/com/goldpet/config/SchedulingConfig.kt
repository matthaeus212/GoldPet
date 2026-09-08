package com.goldpet.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * codegen 프로파일(OpenAPI 스펙 추출용 ephemeral 부팅) 에서는 스케줄러 비활성화.
 * `@Scheduled` 메서드들이 20초짜리 부팅 동안 한 번씩 발화하며 DB/Redis 에 접근해
 * 의미 없는 스택트레이스를 로그에 쌓는 것을 방지.
 */
@Configuration
@Profile("!codegen")
@EnableScheduling
class SchedulingConfig
