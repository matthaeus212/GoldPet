// 장기 미접속 사용자를 휴면(DORMANT)으로 전환하고, 본인 확인 후 해제하는 서비스
package com.goldpet.domain.user.service

import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.common.exception.UnauthorizedException
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * STYLE-001: 휴면 기능은 `UserStatus.DORMANT` enum 과 로그인 차단만 있고 **전환 배치도 해제 API 도
 * 없었다**. 그래서 휴면 사용자가 될 방법 자체가 없었고(라이브 DB 휴면 0명), 안내 화면은 'OOO' 와
 * 가짜 날짜를 하드코딩한 채 방치돼 있었다. 여기서 실제 기능을 구현한다.
 */
@Service
class DormancyService(
    private val userRepository: UserRepository,
    private val systemSettingService: SystemSettingService,
    private val jwtTokenProvider: JwtTokenProvider
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 미접속 며칠부터 휴면으로 볼지. 런타임 설정으로 조정 가능. */
    fun dormancyDays(): Long =
        systemSettingService.getString(DORMANCY_DAYS_KEY, DEFAULT_DORMANCY_DAYS).toLongOrNull()
            ?: DEFAULT_DORMANCY_DAYS.toLong()

    fun enabled(): Boolean =
        systemSettingService.getString(DORMANCY_ENABLED_KEY, "true").equals("true", ignoreCase = true)

    /**
     * 장기 미접속 ACTIVE 사용자를 DORMANT 로 전환한다.
     *
     * 전환은 곧 로그인 차단이므로 배치 한 번의 실수가 그대로 사용자 락아웃이 된다.
     * 그래서 (1) 런타임 플래그로 끌 수 있고, (2) 한 번에 처리할 수 있는 수를 batchSize 로 제한하며,
     * (3) lastLoginAt 이 NULL 인 사용자는 **건드리지 않는다**(기준값이 없는데 휴면 판정할 수 없다.
     * V89 백필로 전원 채웠지만, 이후 유입 경로에서 누락될 여지를 남기지 않는다).
     */
    @Transactional
    fun convertInactiveToDormant(batchSize: Int = DEFAULT_BATCH_SIZE): Int {
        if (!enabled()) {
            log.info("Dormancy conversion skipped: {}=false", DORMANCY_ENABLED_KEY)
            return 0
        }
        val cutoff = LocalDateTime.now().minusDays(dormancyDays())
        val targets = userRepository.findDormancyCandidates(cutoff, PageRequest.of(0, batchSize))
        if (targets.isEmpty()) return 0

        val now = LocalDateTime.now()
        for (user in targets) {
            user.status = UserStatus.DORMANT
            user.dormantAt = now
        }
        userRepository.saveAll(targets)
        log.info("Converted {} user(s) to DORMANT (inactive since before {})", targets.size, cutoff)
        return targets.size
    }

    /**
     * 휴면 해제. [activationToken] 은 로그인 시도에서 자격증명이 검증된 직후에만 발급되는 단기
     * 토큰이라, 이 호출이 본인임을 보증한다(휴면 사용자는 access token 을 받을 수 없다).
     */
    @Transactional
    fun activate(activationToken: String) {
        val userId = jwtTokenProvider.parseDormantActivationUserId(activationToken)
            ?: throw UnauthorizedException("휴면 해제 인증이 만료되었거나 올바르지 않습니다. 다시 로그인해주세요.")

        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found with id: $userId") }

        if (user.status != UserStatus.DORMANT) {
            // 이미 해제된 계정에 대한 재시도는 성공으로 간주한다(사용자가 버튼을 두 번 눌렀을 뿐).
            log.info("Dormant activation no-op: user {} is already {}", userId, user.status)
            return
        }

        user.status = UserStatus.ACTIVE
        user.dormantAt = null
        // 해제 직후 배치가 다시 휴면으로 되돌리지 않도록 접속 시각을 갱신한다.
        user.lastLoginAt = LocalDateTime.now()
        userRepository.save(user)
        log.info("Dormant account activated: user {}", userId)
    }

    companion object {
        const val DORMANCY_ENABLED_KEY = "account.dormancy.enabled"
        const val DORMANCY_DAYS_KEY = "account.dormancy.days"
        const val DEFAULT_DORMANCY_DAYS = "365"
        const val DEFAULT_BATCH_SIZE = 500
    }
}
