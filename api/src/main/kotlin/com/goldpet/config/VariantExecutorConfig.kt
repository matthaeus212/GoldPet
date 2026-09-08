package com.goldpet.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

/**
 * Dedicated executor for async image-variant generation.
 * AbortPolicy + DLQ sweep (WalkSpotVariantSyncScheduler) catches queue overflow.
 * @EnableAsync is already on AsyncConfig.kt.
 */
@Configuration
class VariantExecutorConfig {

    @Bean(name = ["variantExecutor"])
    fun variantExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 4
        maxPoolSize = 8
        queueCapacity = 200
        setThreadNamePrefix("variant-gen-")
        setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
        initialize()
    }
}
