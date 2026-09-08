package com.goldpet.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.client.RestTemplate
import java.util.concurrent.Executor

@Configuration
class StoolAnalysisConfig {

    @Bean(name = ["stoolAnalysisExecutor"])
    fun stoolAnalysisExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.queueCapacity = 50
        executor.setThreadNamePrefix("stool-ai-")
        executor.initialize()
        return executor
    }

    @Bean(name = ["openaiRestTemplate"])
    fun openaiRestTemplate(): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(10_000)
            setReadTimeout(30_000)
        }
        return RestTemplate(factory)
    }
}
