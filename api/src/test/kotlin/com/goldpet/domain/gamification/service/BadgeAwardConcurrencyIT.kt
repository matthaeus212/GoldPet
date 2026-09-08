package com.goldpet.domain.gamification.service

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.gamification.repository.BadgeRepository
import com.goldpet.domain.gamification.repository.UserBadgeRepository
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Critic #12 동시성 회귀 테스트 — BadgeAwardService.checkAndAwardBadges.
 *
 * #1 (tx 오염): 동일 유저·동일 뱃지 동시 award 레이스에서 user row 잠금 직렬화로
 *   DataIntegrityViolationException 이 발생하지 않아야 한다 → 두 tx 모두 정상 커밋(walk+gold 생존),
 *   뱃지 1건·골드 1회만 반영.
 * #3 (골드 cap 레이스): 서로 다른 데일리 미션이 동시에 발화해도 하루 합산 골드가 cap(20) 을 넘지 않아야 한다.
 *
 * 실행 전: `./deploy-local/scripts/start.sh` 로 local PostGIS(5433) 기동 필요.
 */
@Tag("integration")
class BadgeAwardConcurrencyIT : IntegrationTestBase() {

    @Autowired private lateinit var badgeAwardService: BadgeAwardService
    @Autowired private lateinit var badgeRepository: BadgeRepository
    @Autowired private lateinit var userBadgeRepository: UserBadgeRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val ts = System.currentTimeMillis()
    private var userId: Long = 0
    private val createdBadgeIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        userId = jdbcTemplate.queryForObject(
            """INSERT INTO users (oauth_provider, oauth_id, created_at, updated_at)
               VALUES ('LOCAL', 'badge_conc_$ts', NOW(), NOW())
               RETURNING id""",
            Long::class.java
        )!!
    }

    @AfterEach
    fun tearDown() {
        createdBadgeIds.forEach { id ->
            runCatching { jdbcTemplate.update("DELETE FROM user_badges WHERE badge_id = ?", id) }
            runCatching { badgeRepository.deleteById(id) }
        }
        runCatching { jdbcTemplate.update("DELETE FROM gold_transactions WHERE user_id = ?", userId) }
        runCatching { userRepository.deleteById(userId) }
    }

    /** conditionValue=0 → 항상 충족. type 의 실제 카운트(0)와 무관하게 award 됨. */
    private fun dailyBadge(type: BadgeConditionType, rewardGold: Int): Badge {
        val badge = badgeRepository.save(
            Badge(
                name = "conc_${type}_$ts",
                description = "concurrency test badge",
                imageUrl = "",
                conditionType = type,
                conditionValue = 0,
                isActive = true,
                rewardGold = rewardGold,
                isRepeatable = true,
                repeatCycle = "DAILY"
            )
        )
        createdBadgeIds.add(badge.id)
        return badge
    }

    private fun goldBalance(): Int = userRepository.findById(userId).orElseThrow().goldBalance

    @Test
    fun `concurrent same-badge award does not poison tx and grants exactly once`() {
        val badge = dailyBadge(BadgeConditionType.PET_REGISTER, rewardGold = 10)
        val concurrency = 16
        val pool = Executors.newFixedThreadPool(concurrency)
        val gate = CountDownLatch(1)
        val errors = ConcurrentLinkedQueue<String>()

        try {
            val futures = (1..concurrency).map {
                pool.submit {
                    try {
                        gate.await()
                        // 각 호출은 @Transactional → 독립 tx, user-row 잠금으로 직렬화
                        badgeAwardService.checkAndAwardBadges(userId, BadgeConditionType.PET_REGISTER)
                    } catch (e: Throwable) {
                        errors.offer(e.javaClass.simpleName + ": " + e.message)
                    }
                }
            }
            gate.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }

        // 어떤 스레드도 UnexpectedRollbackException / DataIntegrityViolationException 으로 죽지 않아야 함
        assertThat(errors).isEmpty()
        // 뱃지는 정확히 1건만 (유니크 + 직렬화)
        assertThat(userBadgeRepository.countByBadgeId(badge.id)).isEqualTo(1L)
        // 골드는 1회만 지급
        assertThat(goldBalance()).isEqualTo(10)
    }

    @Test
    fun `concurrent distinct daily missions never exceed the 20 gold daily cap`() {
        // 서로 다른 conditionType 데일리 뱃지 3종 × 10골드 = 30 요청 → cap 20 적용 기대
        dailyBadge(BadgeConditionType.PET_REGISTER, rewardGold = 10)
        dailyBadge(BadgeConditionType.FRIEND_MATCH, rewardGold = 10)
        dailyBadge(BadgeConditionType.COMMUNITY_COMMENT, rewardGold = 10)

        val triggers = listOf(
            BadgeConditionType.PET_REGISTER,
            BadgeConditionType.FRIEND_MATCH,
            BadgeConditionType.COMMUNITY_COMMENT
        )
        val pool = Executors.newFixedThreadPool(triggers.size)
        val gate = CountDownLatch(1)
        val errors = ConcurrentLinkedQueue<String>()

        try {
            val futures = triggers.map { trigger ->
                pool.submit {
                    try {
                        gate.await()
                        badgeAwardService.checkAndAwardBadges(userId, trigger)
                    } catch (e: Throwable) {
                        errors.offer(e.javaClass.simpleName + ": " + e.message)
                    }
                }
            }
            gate.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }

        assertThat(errors).isEmpty()
        // 3 뱃지 모두 부여(직렬화) 되나 골드 합산은 cap 20 으로 제한
        assertThat(createdBadgeIds.sumOf { userBadgeRepository.countByBadgeId(it) }).isEqualTo(3L)
        assertThat(goldBalance()).isEqualTo(20)
    }
}
