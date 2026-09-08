// 장기 미접속 사용자를 매일 휴면으로 전환하는 스케줄러
package com.goldpet.domain.user.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * STYLE-001: 휴면 전환 배치. 새벽 4시(백업 03:00 이후)에 하루 한 번 돈다.
 *
 * 전환은 곧 로그인 차단이므로 다중 인스턴스에서 중복 실행되지 않도록 ShedLock 을 건다.
 * 실제 판정·플래그 확인은 DormancyService 가 한다.
 */
@Component
class DormancyScheduler(
    private val dormancyService: DormancyService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "userDormancyConversion", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    fun convertInactiveUsers() {
        val converted = dormancyService.convertInactiveToDormant()
        if (converted > 0) {
            log.info("Dormancy conversion: {} user(s) → DORMANT", converted)
        }
    }
}
