package com.goldpet.domain.metrics.controller

import com.goldpet.IntegrationTestBase
import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Verifies the /api/v1/session/active contract with real beans (no mock library
 * is on the test classpath). Uses the autowired controller directly so the
 * assertions stay deterministic and don't depend on the @Async write completing.
 */
class SessionControllerIT : IntegrationTestBase() {

    @Autowired private lateinit var controller: SessionController
    @Autowired private lateinit var userRepository: UserRepository

    private lateinit var testUser: User

    @BeforeEach
    fun seedUser() {
        testUser = userRepository.findAll().firstOrNull()
            ?: error("Test DB must seed at least one user — run a seed migration or DB-init script first")
    }

    @Test
    fun `records the app-open signal and returns 200 for an authenticated user`() {
        val response = controller.recordActive(UserPrincipal(testUser, null))
        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `returns 401 when there is no authenticated principal`() {
        val response = controller.recordActive(null)
        assertEquals(401, response.statusCode.value())
    }
}
