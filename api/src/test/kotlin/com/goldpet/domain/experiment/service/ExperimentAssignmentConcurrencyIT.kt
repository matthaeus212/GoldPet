package com.goldpet.domain.experiment.service

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.experiment.repository.ExperimentAssignmentRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * write-once 코호트 무결성 통합테스트 (plan §3 통합 / Critic guardrail #2·#3).
 *
 * 검증:
 *  1. 실험 비활성 → CONTROL 반환 + 행 미생성.
 *  2. 실험 활성 → 정확히 1행 생성, 코호트는 TREATMENT|CONTROL, 재호출 시 같은 행/코호트(write-once).
 *  3. 동시 첫노출 레이스(두 스레드 동시 getOrAssignCohort) → 동일 코호트 + 정확히 1행(ON CONFLICT + RE-SELECT).
 *  4. 어드민이 split_pct 를 바꿔도 기존 행은 retroactive 재버킷되지 않음(스냅샷 불변).
 */
@Tag("integration")
class ExperimentAssignmentConcurrencyIT : IntegrationTestBase() {

    @Autowired private lateinit var experimentService: ExperimentService
    @Autowired private lateinit var experimentAssignmentRepository: ExperimentAssignmentRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var systemSettingService: SystemSettingService
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val suffix = UUID.randomUUID().toString().take(8)
    private val key = "compatibility"
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        user = userRepository.save(makeUser("exp_$suffix"))
        // 결정성 위해 salt 고정. split_pct 는 각 테스트에서 설정.
        systemSettingService.setValue("experiment.$key.salt", "it-seed-$suffix")
        systemSettingService.setValue("experiment.$key.salt_version", "1")
    }

    @AfterEach
    fun tearDown() {
        runCatching { jdbcTemplate.update("DELETE FROM experiment_assignment WHERE user_id = ?", user.id) }
        runCatching { userRepository.delete(user) }
        // 다른 테스트 오염 방지 — 실험 플래그 해제.
        runCatching { systemSettingService.setValue("experiment.$key.enabled", "false") }
    }

    private fun rowCount(): Int = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM experiment_assignment WHERE user_id = ? AND experiment_key = ?",
        Int::class.java, user.id, key
    ) ?: 0

    @Test
    fun `disabled experiment returns CONTROL and creates no row`() {
        systemSettingService.setValue("experiment.$key.enabled", "false")

        val cohort = experimentService.getOrAssignCohort(user.id, key)

        assertThat(cohort).isEqualTo(ExperimentService.COHORT_CONTROL)
        assertThat(rowCount()).isZero()
    }

    @Test
    fun `enabled experiment persists exactly one row and is write-once on re-call`() {
        systemSettingService.setValue("experiment.$key.enabled", "true")
        systemSettingService.setValue("experiment.$key.split_pct", "50")

        val first = experimentService.getOrAssignCohort(user.id, key)
        assertThat(first).isIn(ExperimentService.COHORT_TREATMENT, ExperimentService.COHORT_CONTROL)
        assertThat(rowCount()).isEqualTo(1)

        // write-once: 재호출은 같은 코호트 + 추가 행 없음.
        repeat(5) { assertThat(experimentService.getOrAssignCohort(user.id, key)).isEqualTo(first) }
        assertThat(rowCount()).isEqualTo(1)
    }

    @Test
    fun `two simultaneous first-exposures serve the same cohort and create exactly one row`() {
        systemSettingService.setValue("experiment.$key.enabled", "true")
        systemSettingService.setValue("experiment.$key.split_pct", "50")

        val threads = 2
        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks = (1..threads).map {
                Callable {
                    barrier.await() // 두 스레드를 동시에 풀어 INSERT 레이스를 강제.
                    experimentService.getOrAssignCohort(user.id, key)
                }
            }
            val results: List<Future<String>> = pool.invokeAll(tasks)
            val cohorts = results.map { it.get() }

            // 동일 코호트(결정적 버킷 + 승자 RE-SELECT) + 정확히 1행.
            assertThat(cohorts.toSet()).hasSize(1)
            assertThat(rowCount()).isEqualTo(1)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `readCohortOrNone returns NONE for unassigned user and never writes a row`() {
        systemSettingService.setValue("experiment.$key.enabled", "true")
        systemSettingService.setValue("experiment.$key.split_pct", "50")

        // 미노출(미할당) 유저: read-only 조회는 NONE + 행 생성 없음.
        assertThat(experimentService.readCohortOrNone(user.id, key)).isEqualTo(ExperimentService.COHORT_NONE)
        assertThat(rowCount()).isZero()

        // 노출 후에는 배정된 코호트를 그대로 읽는다(재계산 없음, 같은 행).
        val assigned = experimentService.getOrAssignCohort(user.id, key)
        assertThat(experimentService.readCohortOrNone(user.id, key)).isEqualTo(assigned)
        assertThat(rowCount()).isEqualTo(1)
    }

    @Test
    fun `changing split_pct after assignment does not re-bucket the existing row`() {
        systemSettingService.setValue("experiment.$key.enabled", "true")
        systemSettingService.setValue("experiment.$key.split_pct", "100") // 전원 TREATMENT

        val assigned = experimentService.getOrAssignCohort(user.id, key)
        assertThat(assigned).isEqualTo(ExperimentService.COHORT_TREATMENT)

        // 어드민이 0% 로 변경 → 신규 유저만 영향, 기존 행은 불변.
        systemSettingService.setValue("experiment.$key.split_pct", "0")
        assertThat(experimentService.getOrAssignCohort(user.id, key)).isEqualTo(ExperimentService.COHORT_TREATMENT)
        assertThat(rowCount()).isEqualTo(1)
    }

    private fun makeUser(oauthId: String) = User(
        id = 0,
        email = "$oauthId@goldpet.com",
        oauthProvider = "LOCAL",
        oauthId = oauthId,
        username = oauthId,
        password = passwordEncoder.encode("pass"),
        nickname = oauthId,
        name = oauthId,
        birthDate = null,
        phoneNumber = null,
        gender = null,
        birthYear = null,
        mainLocationText = null,
        mainLocationGeom = null,
        profileImageUrl = null,
    )
}
