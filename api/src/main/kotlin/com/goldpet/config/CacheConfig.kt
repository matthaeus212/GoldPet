package com.goldpet.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@org.springframework.context.annotation.Profile("!test")
class CacheConfig {

    @Bean
    fun cacheManager(redisConnectionFactory: RedisConnectionFactory): RedisCacheManager {
        val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())

        // Kotlin data class는 final이므로 NON_FINAL 대신 EVERYTHING 사용
        // (NON_FINAL은 final 타입에 @class 타입 정보를 포함하지 않아 역직렬화 실패)
        // BasicPolymorphicTypeValidator를 명시하여 deprecated LaissezFaireSubTypeValidator 경고 제거
        val ptv = com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Any::class.java)
            .build()
        objectMapper.activateDefaultTyping(
            ptv,
            com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.EVERYTHING,
            com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        )

        val serializer = GenericJackson2JsonRedisSerializer(objectMapper)

        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )
            .entryTtl(Duration.ofMinutes(10)) // Default TTL

        val postFeedConfig = defaultConfig.entryTtl(Duration.ofSeconds(30)) // Short TTL for feeds
        val staticDataConfig = defaultConfig.entryTtl(Duration.ofHours(24)) // Long TTL for categories

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("community:posts", postFeedConfig)
            .withCacheConfiguration("community:categories", staticDataConfig)
            .withCacheConfiguration("pet:species", staticDataConfig)
            .withCacheConfiguration("breeds", staticDataConfig)
            .withCacheConfiguration("system:settings", staticDataConfig)
            .withCacheConfiguration("user:interests", staticDataConfig)
            .withCacheConfiguration("user:hobbies", staticDataConfig)
            .withCacheConfiguration("gold:products", staticDataConfig)
            .withCacheConfiguration("user:stats", postFeedConfig)
            .withCacheConfiguration("emoticons", staticDataConfig)
            .withCacheConfiguration("ai:loading-tips", staticDataConfig)
            // 공개 배너: admin 쓰기 @CacheEvict 가 1차 무효화. 짧은 TTL 은 예약 배너의
            // 시간 기반 노출 전환을 흡수하고 evict 누락 시 안전망 역할.
            .withCacheConfiguration("banners:public", postFeedConfig)
            .build()
    }

    @EventListener(ApplicationReadyEvent::class)
    fun clearCachesOnStartup(event: ApplicationReadyEvent) {
        val log = LoggerFactory.getLogger(CacheConfig::class.java)
        // Spec 생성(codegen) 시에는 실제 Redis 가 없어 예외가 SpringApplication 을
        // 죽이므로 skip. Exception 을 던지면 EventPublishingRunListener.ready() 가
        // run failure 로 승격시켜 앱이 tear-down 되면서 /v3/api-docs 도 닫혀버림.
        if (event.applicationContext.environment.activeProfiles.contains("codegen")) {
            log.info("Skipping Redis cache clear on codegen profile (no live Redis expected)")
            return
        }
        val cm = event.applicationContext.getBean(RedisCacheManager::class.java)
        cm.cacheNames.forEach { name ->
            cm.getCache(name)?.clear()
        }
        log.info("All Redis caches cleared on startup (Flyway migration sync)")
    }
}
