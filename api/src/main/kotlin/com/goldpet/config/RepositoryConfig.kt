package com.goldpet.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["com.goldpet.domain"])
@EnableRedisRepositories(
    basePackages = ["com.goldpet.redis"],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.REGEX, pattern = ["com\\.goldpet\\.domain\\..*"])
    ]
)
class RepositoryConfig
