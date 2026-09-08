package com.goldpet.domain.community.controller

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * HTTP contract + N+1 guard for GET /api/v1/users/{userId}/community/posts.
 *
 * Covers: 200+nextCursor, second-page via cursor, N+1 queryCount≤6, 401 anon.
 * Unit-level cursor logic (sentinel, clamp, invalid cursor) is in CommunityAuthorPostsServiceTest.
 *
 * Posts are inserted via jdbcTemplate (category_id=1 "WALK" pre-seeded by Flyway).
 * File attachments are inserted the same way to exercise the LEFT JOIN FETCH path.
 */
@AutoConfigureMockMvc
@Tag("integration")
class CommunityAuthorPostsControllerIT : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val ts = System.currentTimeMillis()
    private lateinit var viewer: User
    private lateinit var author: User

    /** IDs for the 31 pagination posts created in setUp — deleted in tearDown. */
    private val paginationPostIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        viewer = userRepository.save(makeUser("cap_viewer_$ts"))
        author = userRepository.save(makeUser("cap_author_$ts"))

        // 31 posts for pagination tests — newest has smallest offset (i=1), so DESC order: i=1 first.
        for (i in 1..31) {
            val postId = jdbcTemplate.queryForObject(
                """
                INSERT INTO community_posts
                    (user_id, category_id, title, content, visibility, view_count, like_count, post_type, is_hidden, created_at, updated_at)
                VALUES
                    (?, 1, 'post $i ts $ts', 'content', 'PUBLIC', 0, 0, 'GENERAL', false,
                     NOW() - INTERVAL '$i seconds', NOW())
                RETURNING id
                """.trimIndent(),
                Long::class.java, author.id
            )!!
            paginationPostIds += postId
        }
    }

    @AfterEach
    fun tearDown() {
        if (paginationPostIds.isNotEmpty()) {
            val ids = paginationPostIds.joinToString(",")
            runCatching { jdbcTemplate.update("DELETE FROM community_post_images WHERE post_id IN ($ids)") }
            runCatching { jdbcTemplate.update("DELETE FROM community_posts WHERE id IN ($ids)") }
            paginationPostIds.clear()
        }
        runCatching { userRepository.delete(author) }
        runCatching { userRepository.delete(viewer) }
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `GET community posts returns 200 with 30 posts and nextCursor when author has 31 posts`() {
        mockMvc.perform(
            get("/api/v1/users/${author.id}/community/posts")
                .header("Authorization", "Bearer ${userToken(viewer)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.posts").isArray)
            .andExpect(jsonPath("$.posts.length()").value(30))
            .andExpect(jsonPath("$.nextCursor").isNotEmpty)
    }

    @Test
    fun `GET community posts second page via cursor returns remaining 1 post without nextCursor`() {
        // Fetch first page to get cursor
        val firstPage = mockMvc.perform(
            get("/api/v1/users/${author.id}/community/posts")
                .header("Authorization", "Bearer ${userToken(viewer)}")
        )
            .andExpect(status().isOk)
            .andReturn()

        val body = firstPage.response.contentAsString
        val cursorMatch = Regex(""""nextCursor"\s*:\s*"([^"]+)"""").find(body)
        val cursor = requireNotNull(cursorMatch?.groupValues?.get(1)) {
            "nextCursor must be present in first-page response"
        }

        // Second page using that cursor
        mockMvc.perform(
            get("/api/v1/users/${author.id}/community/posts")
                .param("cursor", cursor)
                .header("Authorization", "Bearer ${userToken(viewer)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.posts.length()").value(1))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
    }

    @Test
    fun `GET community posts does not issue N+1 queries for post image collections`() {
        // setUp provides 31 posts; page 1 returns 30 of them.
        // Without LEFT JOIN FETCH, Hibernate fires one collection-init SELECT per post
        // to lazily load `images` → 30 extra statements, total ≫ budget.
        // With LEFT JOIN FETCH the single JPQL covers posts + images + files → total ≤ 6.
        withinStatementBudget(6) {
            mockMvc.perform(
                get("/api/v1/users/${author.id}/community/posts")
                    .header("Authorization", "Bearer ${userToken(viewer)}")
            ).andExpect(status().isOk)
        }
    }

    @Test
    fun `GET community posts returns 401 without authentication`() {
        mockMvc.perform(get("/api/v1/users/${author.id}/community/posts"))
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
