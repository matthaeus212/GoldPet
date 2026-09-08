package com.goldpet.domain.user.controller

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.friend.entity.UserBlock
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * HTTP contract tests for GET /api/v1/users/{userId}/public-profile.
 *
 * Verifies: 200 normal, 404 BLOCKED_ME, 200 BLOCKED_BY_ME, 404 DEACTIVATED, 401 anon.
 * Unit-level behaviour (service logic, PII exclusion) is covered in UserPublicProfileServiceTest.
 */
@AutoConfigureMockMvc
@Tag("integration")
class UserPublicProfileControllerIT : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var userBlockRepository: UserBlockRepository
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val ts = System.currentTimeMillis()
    private lateinit var viewer: User
    private lateinit var target: User
    private val savedBlocks = mutableListOf<UserBlock>()

    @BeforeEach
    fun setUp() {
        viewer = userRepository.save(makeUser("upp_viewer_$ts"))
        target = userRepository.save(makeUser("upp_target_$ts"))
    }

    @AfterEach
    fun tearDown() {
        savedBlocks.forEach { runCatching { userBlockRepository.delete(it) } }
        savedBlocks.clear()
        runCatching { userRepository.delete(target) }
        runCatching { userRepository.delete(viewer) }
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `GET public-profile returns 200 with NORMAL status for active unblocked target`() {
        mockMvc.perform(
            get("/api/v1/users/${target.id}/public-profile")
                .header("Authorization", "Bearer ${userToken(viewer)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(target.id))
            .andExpect(jsonPath("$.status").value("NORMAL"))
            .andExpect(jsonPath("$.isMe").value(false))
            .andExpect(jsonPath("$.blockedByMe").value(false))
    }

    @Test
    fun `GET public-profile returns 404 when target has blocked the viewer`() {
        savedBlocks += userBlockRepository.save(UserBlock(blocker = target, blocked = viewer))

        mockMvc.perform(
            get("/api/v1/users/${target.id}/public-profile")
                .header("Authorization", "Bearer ${userToken(viewer)}")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET public-profile returns 200 with BLOCKED_BY_ME and anonymized fields when viewer has blocked target`() {
        savedBlocks += userBlockRepository.save(UserBlock(blocker = viewer, blocked = target))

        mockMvc.perform(
            get("/api/v1/users/${target.id}/public-profile")
                .header("Authorization", "Bearer ${userToken(viewer)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("BLOCKED_BY_ME"))
            .andExpect(jsonPath("$.nickname").doesNotExist())
            .andExpect(jsonPath("$.publicPostCount").value(0))
            .andExpect(jsonPath("$.blockedByMe").value(true))
    }

    @Test
    fun `GET public-profile returns 404 when target is DEACTIVATED`() {
        target.status = UserStatus.WITHDRAWN
        userRepository.save(target)

        mockMvc.perform(
            get("/api/v1/users/${target.id}/public-profile")
                .header("Authorization", "Bearer ${userToken(viewer)}")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET public-profile returns 401 without authentication`() {
        mockMvc.perform(get("/api/v1/users/${target.id}/public-profile"))
            .andExpect(status().isUnauthorized)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun userToken(user: User): String {
        val principal = UserPrincipal.create(user)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
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
