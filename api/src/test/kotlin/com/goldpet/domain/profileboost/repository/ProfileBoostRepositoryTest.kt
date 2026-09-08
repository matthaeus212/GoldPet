package com.goldpet.domain.profileboost.repository

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.profileboost.entity.ProfileBoost
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.math.absoluteValue
import java.util.UUID

/**
 * 회귀 테스트: `findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc` 가 만료(expiry)를 올바르게 처리.
 *   - 미만료 부스트만 반환, 만료된 부스트는 미반환 → "expiry works".
 *   - 여러 활성 부스트 중 가장 늦게 만료되는 1건 반환.
 */
@Tag("integration")
class ProfileBoostRepositoryTest : IntegrationTestBase() {

    @Autowired private lateinit var repository: ProfileBoostRepository

    private val userId = (UUID.randomUUID().mostSignificantBits.absoluteValue % 1_000_000_000) + 1
    private val saved = mutableListOf<ProfileBoost>()

    @AfterEach
    fun tearDown() {
        runCatching { repository.deleteAll(saved) }
        saved.clear()
    }

    @Test
    fun `active boost is returned and expired boost is ignored`() {
        val now = LocalDateTime.now()
        saved += repository.save(
            ProfileBoost(userId = userId, startedAt = now.minusHours(2), expiresAt = now.minusHours(1), goldCost = 30)
        )
        val active = repository.save(
            ProfileBoost(userId = userId, startedAt = now.minusMinutes(5), expiresAt = now.plusMinutes(25), goldCost = 30)
        )
        saved += active

        val found = repository.findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(userId, now)

        assertThat(found).isNotNull
        assertThat(found!!.id).isEqualTo(active.id)
    }

    @Test
    fun `returns null when all boosts expired`() {
        val now = LocalDateTime.now()
        saved += repository.save(
            ProfileBoost(userId = userId, startedAt = now.minusHours(2), expiresAt = now.minusMinutes(1), goldCost = 30)
        )

        val found = repository.findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(userId, now)

        assertThat(found).isNull()
    }

    @Test
    fun `returns latest expiring among multiple active boosts`() {
        val now = LocalDateTime.now()
        saved += repository.save(
            ProfileBoost(userId = userId, startedAt = now, expiresAt = now.plusMinutes(10), goldCost = 30)
        )
        val longer = repository.save(
            ProfileBoost(userId = userId, startedAt = now, expiresAt = now.plusMinutes(40), goldCost = 30)
        )
        saved += longer

        val found = repository.findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(userId, now)

        assertThat(found!!.id).isEqualTo(longer.id)
    }
}
