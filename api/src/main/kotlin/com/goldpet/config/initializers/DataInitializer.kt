package com.goldpet.config.initializers

import com.goldpet.domain.common.service.SystemSettingService
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!codegen")
class DataInitializer(
    private val systemSettingService: SystemSettingService
) {

    @Bean
    fun initSystemSettings(): CommandLineRunner {
        return CommandLineRunner {
            if (systemSettingService.getInt("COMMUNITY_POST_PREVIEW_LENGTH", -1) == -1) {
                systemSettingService.setValue(
                    "COMMUNITY_POST_PREVIEW_LENGTH", 
                    "100", 
                    "Length of community post content preview in list view"
                )
            }
        }
    }
}
