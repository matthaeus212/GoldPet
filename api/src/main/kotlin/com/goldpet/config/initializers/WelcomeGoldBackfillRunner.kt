// 회원가입 축하 골드를 받지 못한 기존 회원에게 1회 소급 지급하는 원타임 러너
package com.goldpet.config.initializers

import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!codegen")
class WelcomeGoldBackfillRunner(
    private val systemSettingService: SystemSettingService,
    private val userRepository: UserRepository,
    private val goldService: GoldService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val FLAG_DONE = "signup.welcome.gold.backfill.done"
    }

    @Bean
    fun backfillWelcomeGold(): CommandLineRunner = CommandLineRunner {
        if (systemSettingService.getBoolean(FLAG_DONE, false)) {
            return@CommandLineRunner
        }

        val targets = userRepository.findAllBySignupCompletedAtIsNotNullAndStatus(UserStatus.ACTIVE)
        var granted = 0
        var failed = 0
        targets.forEach { user ->
            try {
                if (goldService.grantWelcomeGoldIfNeeded(user.id) != null) {
                    granted++
                }
            } catch (ex: Exception) {
                failed++
                log.warn("Welcome gold backfill failed for user {}: {}", user.id, ex.message)
            }
        }

        // 실패가 있으면 플래그를 남기지 않아 다음 기동 때 재시도 (지급 이력으로 중복 지급은 차단됨)
        if (failed == 0) {
            systemSettingService.setValue(FLAG_DONE, "true", "회원가입 축하 골드 소급 지급 완료 플래그")
        }
        log.info(
            "Welcome gold backfill: granted={}, skipped={}, failed={} (total {})",
            granted, targets.size - granted - failed, failed, targets.size
        )
    }
}
