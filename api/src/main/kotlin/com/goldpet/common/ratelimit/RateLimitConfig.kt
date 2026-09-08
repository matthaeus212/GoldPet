package com.goldpet.common.ratelimit

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * community-author-profile-gallery §4-1 — Bucket4j-redis distributed rate-limit infra.
 *
 * - Spring Data Redis 의 `LettuceConnectionFactory` 와는 독립된 전용 `RedisClient` 를 띄운다.
 *   Bucket4j-redis 는 `StatefulRedisConnection<byte[], byte[]>` 기반 CAS 스크립트를 사용하므로
 *   공용 factory 의 serializer 설정과 충돌을 피하기 위함.
 * - 버킷 키 서픽스 별 expiration: 슬라이딩 윈도우(1 분) 이므로 마지막 리필 기준으로 2 분 후 제거.
 * - Redis 주소/비밀번호는 Spring Boot 가 자동 바인딩하는 `spring.data.redis.*` 를 재사용.
 *
 * 단일 인스턴스 배포에서도 동일 코드로 동작 (redis 단일 노드 OK).
 */
@Configuration
@EnableConfigurationProperties(UserProfileRateLimitProperties::class)
@ConditionalOnProperty(
    prefix = "app.rate-limit.user-profile",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class RateLimitConfig {

    @Bean(destroyMethod = "shutdown")
    fun rateLimitRedisClient(redisProperties: RedisProperties): RedisClient {
        val builder = RedisURI.Builder.redis(redisProperties.host, redisProperties.port)
            .withDatabase(redisProperties.database)
        val password = redisProperties.password
        if (!password.isNullOrBlank()) {
            builder.withPassword(password.toCharArray())
        }
        val username = redisProperties.username
        if (!username.isNullOrBlank()) {
            builder.withAuthentication(username, password?.toCharArray() ?: CharArray(0))
        }
        return RedisClient.create(builder.build())
    }

    @Bean
    fun rateLimitProxyManager(rateLimitRedisClient: RedisClient): ProxyManager<ByteArray> {
        val clientConfig = ClientSideConfig.getDefault()
            .withExpirationAfterWriteStrategy(
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                    Duration.ofMinutes(2),
                ),
            )
        return LettuceBasedProxyManager.builderFor(rateLimitRedisClient)
            .withClientSideConfig(clientConfig)
            .build()
    }
}
