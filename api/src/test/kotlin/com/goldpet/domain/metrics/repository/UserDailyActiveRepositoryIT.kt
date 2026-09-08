package com.goldpet.domain.metrics.repository

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

/**
 * Verifies the ON CONFLICT DO NOTHING upsert: exactly one row per user per day.
 *
 * Requires the local PostGIS container (localhost:5433) — see [IntegrationTestBase].
 * NOT @Transactional on purpose: [IntegrationTestBase] drains the whole Hikari pool
 * in a @BeforeEach (advisory-lock cleanup), which deadlocks if a test transaction is
 * holding a connection. So we commit and clean up explicitly instead (same pattern as
 * IdempotencyConcurrencyIT). insertIfAbsent is self-transactional.
 */
class UserDailyActiveRepositoryIT : IntegrationTestBase() {

    @Autowired private lateinit var repository: UserDailyActiveRepository
    @Autowired private lateinit var userRepository: UserRepository

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        testUser = userRepository.findAll().firstOrNull()
            ?: error("Test DB must seed at least one user — run a seed migration or DB-init script first")
        // Clean slate for deterministic counts (e.g. a row committed by SessionControllerIT's async write).
        repository.deleteByUserId(testUser.id)
    }

    @AfterEach
    fun cleanUp() {
        repository.deleteByUserId(testUser.id)
    }

    @Test
    fun `insertIfAbsent records exactly one row per user per day`() {
        val today = LocalDate.now()

        val firstInsert = repository.insertIfAbsent(testUser.id, today)
        val secondInsert = repository.insertIfAbsent(testUser.id, today)

        assertEquals(1, firstInsert, "first call should insert a row")
        assertEquals(0, secondInsert, "duplicate (same user, same day) must be a no-op via ON CONFLICT")
        assertEquals(1L, repository.countByUserId(testUser.id))
    }

    @Test
    fun `a different day produces a separate row`() {
        repository.insertIfAbsent(testUser.id, LocalDate.now())
        repository.insertIfAbsent(testUser.id, LocalDate.now().minusDays(1))

        assertEquals(2L, repository.countByUserId(testUser.id))
    }
}
