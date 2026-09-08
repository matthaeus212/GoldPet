package com.goldpet.domain.notification.reengagement

import com.goldpet.IntegrationTestBase
import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.notification.reengagement.entity.ReengagementSend
import com.goldpet.domain.notification.reengagement.repository.ReengagementSendRepository
import com.goldpet.domain.user.entity.DeviceType
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserDevice
import com.goldpet.domain.user.repository.UserDeviceRepository
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * W2c 재참여 후보 쿼리 IT — opt-out 존중 / dedup 제외 / 콜드스타트(streak row 부재) tolerant 를 DB 로 검증.
 * 실행 전: `./deploy-local/scripts/start.sh` 로 local PostGIS(5433) 기동.
 */
@Tag("integration")
class ReengagementCandidateIT : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var userDeviceRepository: UserDeviceRepository
    @Autowired private lateinit var reengagementSendRepository: ReengagementSendRepository
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val uid: String = UUID.randomUUID().toString().replace("-", "").take(10)
    private val kstToday: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val createdUserIds = mutableListOf<Long>()

    private fun newUser(suffix: String, reengagementEnabled: Boolean): User {
        val u = userRepository.save(
            User(
                id = 0,
                email = "re_${suffix}_$uid@goldpet.com",
                emailHash = BlindIndexUtil.hash("re_${suffix}_$uid@goldpet.com"),
                oauthProvider = "LOCAL",
                oauthId = "re_${suffix}_$uid",
                username = null, password = null, nickname = "Re_${suffix}_$uid",
                name = null, birthDate = null, phoneNumber = null, gender = null,
                birthYear = null, mainLocationText = null, mainLocationGeom = null,
                profileImageUrl = null,
            ).apply { isReengagementAlertEnabled = reengagementEnabled }
        )
        createdUserIds += u.id
        userDeviceRepository.save(
            UserDevice(
                user = u, deviceId = "dev-$suffix-$uid", fcmToken = "tok-$suffix-$uid",
                deviceType = DeviceType.ANDROID, isActive = true,
            )
        )
        return u
    }

    private fun seedStreak(userId: Long, currentStreak: Int, lastActive: LocalDate) {
        jdbc.update(
            "INSERT INTO user_streaks(user_id, current_streak, longest_streak, last_active_date, freeze_count, updated_at) " +
                "VALUES (?, ?, ?, ?, 0, now())",
            userId, currentStreak, currentStreak, lastActive
        )
    }

    private lateinit var optIn: User
    private lateinit var optOut: User
    private lateinit var noStreak: User

    @BeforeEach
    fun setUp() {
        optIn = newUser("in", reengagementEnabled = true)
        optOut = newUser("out", reengagementEnabled = false)
        noStreak = newUser("nostreak", reengagementEnabled = true)
        // optIn, optOut: streak 위기(어제까지 활동, 오늘 무활동). noStreak: user_streaks row 없음.
        seedStreak(optIn.id, currentStreak = 5, lastActive = kstToday.minusDays(1))
        seedStreak(optOut.id, currentStreak = 5, lastActive = kstToday.minusDays(1))
    }

    @AfterEach
    fun tearDown() {
        createdUserIds.forEach { id ->
            runCatching { jdbc.update("DELETE FROM reengagement_sends WHERE user_id = ?", id) }
            runCatching { jdbc.update("DELETE FROM user_streaks WHERE user_id = ?", id) }
            runCatching { userDeviceRepository.deleteAllByUserId(id) }
            runCatching { userRepository.deleteById(id) }
        }
    }

    @Test
    fun `스트릭-위기 후보는 opt-in 유저만 포함하고 opt-out은 제외한다`() {
        val ids = reengagementSendRepository.findStreakAtRiskCandidateUserIds(kstToday, 100)

        assertThat(ids).contains(optIn.id)
        assertThat(ids).doesNotContain(optOut.id) // 동의 우회 방지 — opt-out 존중
    }

    @Test
    fun `user_streaks row 가 없는 유저는 스트릭-위기 후보에서 자연 제외된다 (콜드스타트 tolerant)`() {
        val ids = reengagementSendRepository.findStreakAtRiskCandidateUserIds(kstToday, 100)

        assertThat(ids).doesNotContain(noStreak.id)
    }

    @Test
    fun `오늘 이미 발송된 유저는 dedup으로 후보에서 제외된다`() {
        // 사전: optIn 후보 포함 확인.
        assertThat(reengagementSendRepository.findStreakAtRiskCandidateUserIds(kstToday, 100)).contains(optIn.id)

        // optIn 에 오늘 dedup row 기록.
        reengagementSendRepository.save(
            ReengagementSend(userId = optIn.id, sendDate = kstToday, nudgeType = "STREAK_AT_RISK")
        )

        val ids = reengagementSendRepository.findStreakAtRiskCandidateUserIds(kstToday, 100)
        assertThat(ids).doesNotContain(optIn.id)
    }

    @Test
    fun `휴면 후보 native 쿼리가 정상 실행된다`() {
        // 산책 이력이 없으므로 본 유저들은 후보가 아님 — 쿼리 실행/SQL 유효성 검증 목적.
        val ids = reengagementSendRepository.findDormancyCandidateUserIds(
            dormantSince = kstToday.minusDays(3).atStartOfDay(),
            churnFloor = kstToday.minusDays(14).atStartOfDay(),
            today = kstToday,
            limit = 100
        )
        assertThat(ids).doesNotContain(optIn.id, optOut.id, noStreak.id)
    }
}
