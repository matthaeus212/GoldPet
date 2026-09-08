package com.goldpet.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * ShedLock — 멀티인스턴스(스케일아웃) 환경에서 `@Scheduled` 잡의 중복발화 방지.
 *
 * **LockProvider = JDBC(shedlock 테이블, V82).** 공유 캐시 Redis 는 `maxmemory-policy=allkeys-lru`
 * (인스턴스 전역 정책)라 TTL 기반 락 키가 evict 대상이 되어 락이 증발 → 중복실행(비안전측) 위험.
 * DB 영속 JDBC provider 는 eviction 면역이며 Redis 토폴로지 변경이 0이다.
 *
 * **`usingDbTime()`**: 인스턴스 간 시계 편차를 제거하기 위해 락 만료 판정을 DB 서버 시각으로 수행.
 *
 * **`@Profile("!codegen")`**: [SchedulingConfig] 와 동일 가드. codegen(OpenAPI 추출용 ephemeral 부팅)
 * 에서는 `@EnableScheduling` 이 꺼져 잡이 발화하지 않으므로 락 머신러리/LockProvider 빈이 불필요.
 * test 프로파일에서는 활성이며, 동일 DataSource + Flyway V82 로 `shedlock` 테이블이 생성되므로
 * [JdbcTemplateLockProvider] 가 정상 부팅한다(별도 인메모리 provider 불필요 — 부팅 회귀 없음).
 *
 * `@EnableScheduling` 은 [SchedulingConfig] 에 분리 위치. `@EnableSchedulerLock` 은 `@SchedulerLock`
 * 메서드 인터셉터(advisor)만 등록하므로 두 애너테이션의 위치 분리는 무해하다.
 */
@Configuration
@Profile("!codegen")
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
class ShedLockConfig {

    @Bean
    fun lockProvider(dataSource: DataSource): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        )
}
