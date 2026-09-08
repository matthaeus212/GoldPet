package com.goldpet.domain.friend.repository

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.friend.entity.UserBlock
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

/**
 * 회귀 테스트: `UserBlockRepository.findBlockedUserIds` 는 native UNION으로 양방향 차단을 반환한다.
 *
 * 검증 항목:
 *   1. viewer → author 차단 (outbound)  → findBlockedUserIds(viewerId) 에 authorId 포함
 *   2. author → viewer 차단 (inbound)   → findBlockedUserIds(viewerId) 에 authorId 포함 (UNION 두 번째 arm)
 *   3. 관련 없는 차단 관계               → viewerId 결과에 미포함
 *   4. 양방향 상호 차단                  → UNION 중복 제거 — authorId 정확히 1회만
 *   5. viewer 자신의 id 미포함           → UNION이 viewer.id를 반환하지 않음
 *
 * plan: `.omc/plans/community-author-profile-gallery.md` §6 Unit — `UserBlockRepository.findBlockedUserIds`
 * impl: `UserBlockRepository.kt:16-21`
 */
@Tag("integration")
class UserBlockRepositoryBidirectionalTest : IntegrationTestBase() {

    @Autowired private lateinit var userBlockRepository: UserBlockRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val suffix = UUID.randomUUID().toString().take(8)
    private lateinit var viewer: User
    private lateinit var author: User
    private lateinit var unrelated: User

    /** Tracks UserBlock entities saved during each test for deterministic cleanup. */
    private val savedBlocks = mutableListOf<UserBlock>()

    @BeforeEach
    fun setUpUsers() {
        viewer    = userRepository.save(makeUser("ubr_viewer_$suffix"))
        author    = userRepository.save(makeUser("ubr_author_$suffix"))
        unrelated = userRepository.save(makeUser("ubr_unrel_$suffix"))
    }

    @AfterEach
    fun tearDown() {
        // Blocks first (FK→users), then users
        runCatching { userBlockRepository.deleteAll(savedBlocks) }
        savedBlocks.clear()
        runCatching { userRepository.delete(viewer) }
        runCatching { userRepository.delete(author) }
        runCatching { userRepository.delete(unrelated) }
    }

    // ── 1. outbound ──────────────────────────────────────────────────────────

    @Test
    fun `findBlockedUserIds includes author when viewer blocked author (outbound)`() {
        savedBlocks += userBlockRepository.save(UserBlock(blocker = viewer, blocked = author))

        val result = userBlockRepository.findBlockedUserIds(viewer.id)

        assertThat(result).contains(author.id)
        assertThat(result).doesNotContain(unrelated.id)
    }

    // ── 2. inbound (UNION second arm) ────────────────────────────────────────

    @Test
    fun `findBlockedUserIds includes author when author blocked viewer (inbound UNION)`() {
        savedBlocks += userBlockRepository.save(UserBlock(blocker = author, blocked = viewer))

        val result = userBlockRepository.findBlockedUserIds(viewer.id)

        assertThat(result).contains(author.id)
        assertThat(result).doesNotContain(unrelated.id)
    }

    // ── 3. unrelated block does not leak into viewer's list ──────────────────

    @Test
    fun `findBlockedUserIds returns empty for viewer when only unrelated block exists`() {
        // unrelated → author block has nothing to do with viewer
        savedBlocks += userBlockRepository.save(UserBlock(blocker = unrelated, blocked = author))

        val result = userBlockRepository.findBlockedUserIds(viewer.id)

        assertThat(result)
            .`as`("viewer has no block relationship — result must be empty")
            .doesNotContain(author.id, unrelated.id)
    }

    // ── 4. mutual block — UNION deduplication ────────────────────────────────

    @Test
    fun `findBlockedUserIds deduplicates author id when mutual block exists`() {
        savedBlocks += userBlockRepository.save(UserBlock(blocker = viewer, blocked = author))
        savedBlocks += userBlockRepository.save(UserBlock(blocker = author, blocked = viewer))

        val result = userBlockRepository.findBlockedUserIds(viewer.id)

        // SQL UNION (not UNION ALL) removes duplicates — author.id appears exactly once
        assertThat(result.count { it == author.id })
            .`as`("UNION must deduplicate — author.id should appear exactly once")
            .isEqualTo(1)
    }

    // ── 5. viewer's own id never appears ─────────────────────────────────────

    @Test
    fun `findBlockedUserIds never returns viewer own id`() {
        savedBlocks += userBlockRepository.save(UserBlock(blocker = viewer, blocked = author))

        val result = userBlockRepository.findBlockedUserIds(viewer.id)

        assertThat(result)
            .`as`("viewer's own id must never appear in the blocked set")
            .doesNotContain(viewer.id)
    }

    // ── helper ───────────────────────────────────────────────────────────────

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
