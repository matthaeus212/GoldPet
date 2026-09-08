package com.goldpet.domain.friend.service

import com.goldpet.IntegrationTestBase
import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.experiment.service.ExperimentService
import com.goldpet.domain.friend.entity.LikeStatus
import com.goldpet.domain.friend.repository.LikeRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * FIX-A #3 — like_events 트랜잭션 격리 **실 DB** 통합 테스트 (mock 이 아님).
 *
 * 검증: like_events insert 가 실제 DB 에서 실패해도(여기서는 cohort CHECK 위반을 주입) **좋아요 자체는 커밋**된다.
 * [LikeEventTxWriter.persist] 가 REQUIRES_NEW 로 outer 좋아요 트랜잭션을 suspend 하므로 inner 실패가
 * outer 를 rollback-only 로 오염시키지 못한다(예전 BadgeAwardService 회귀 클래스의 가드).
 *
 * 주입 방법: [ExperimentService.readCohortOrNone] 를 mock 하여 CHECK IN('TREATMENT','CONTROL','NONE') 를
 * 위반하는 잘못된 코호트 문자열을 반환 → IDENTITY insert 시 DataIntegrityViolationException →
 * recorder 가 best-effort 로 삼킴. 기존 mock 기반 LikeEventTxWriterTest 와 달리 실제 트랜잭션 경계를 통과시킨다.
 *
 * 실행 전: `./deploy-local/scripts/start.sh` 로 local PostGIS(5433) 기동 (SOLO — shared DB).
 */
@Tag("integration")
class LikeEventRollbackIsolationIT : IntegrationTestBase() {

    @Autowired private lateinit var likeService: LikeService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var likeRepository: LikeRepository
    @Autowired private lateinit var jdbc: JdbcTemplate

    // 실 DB CHECK 위반을 주입하기 위해 cohort read 를 잘못된 값으로 강제.
    @MockBean private lateinit var experimentService: ExperimentService

    private val uid: String = UUID.randomUUID().toString().replace("-", "").take(10)
    private lateinit var liker: User
    private lateinit var target: User

    @BeforeEach
    fun setUp() {
        liker = userRepository.save(newUser("liker_$uid"))
        target = userRepository.save(newUser("target_$uid"))
        // 잘못된 코호트 → like_events.cohort CHECK 위반을 유발 (격리 안 되면 좋아요까지 롤백됨).
        whenever(experimentService.readCohortOrNone(any(), any())).thenReturn("BOGUS_COHORT")
    }

    @AfterEach
    fun tearDown() {
        runCatching { jdbc.update("DELETE FROM like_events WHERE user_id IN (?, ?)", liker.id, target.id) }
        runCatching { jdbc.update("DELETE FROM likes WHERE from_user_id IN (?, ?) OR to_user_id IN (?, ?)", liker.id, target.id, liker.id, target.id) }
        runCatching { jdbc.update("DELETE FROM notifications WHERE user_id IN (?, ?)", liker.id, target.id) }
        runCatching { userRepository.delete(liker) }
        runCatching { userRepository.delete(target) }
    }

    @Test
    fun `like commits even when the like_events append fails against the real DB`() {
        // When — append will fail (bad cohort). This call must NOT throw: if the REQUIRES_NEW
        // isolation were missing, the outer tx would be rollback-only and likeUser would throw
        // UnexpectedRollbackException at commit (failing this test).
        val response = likeService.likeUser(liker.id, target.id, "compatible")

        // Then — the like itself is committed (outer tx survived the inner REQUIRES_NEW failure).
        assertThat(response.toUserId).isEqualTo(target.id)
        assertThat(
            likeRepository.existsByFromUserIdAndToUserIdAndStatus(liker.id, target.id, LikeStatus.ACTIVE)
        ).withFailMessage("like must persist even though like_events append failed").isTrue()

        // And — no like_events row was written (the bad-cohort insert rolled back in its own tx).
        val eventCount = jdbc.queryForObject(
            "SELECT count(*) FROM like_events WHERE user_id = ?", Long::class.java, liker.id
        )
        assertThat(eventCount).withFailMessage("failed like_events insert must not persist a row").isEqualTo(0L)
    }

    private fun newUser(handle: String) = User(
        id = 0,
        email = "$handle@goldpet.com",
        emailHash = BlindIndexUtil.hash("$handle@goldpet.com"),
        oauthProvider = "LOCAL",
        oauthId = handle,
        username = null,
        password = null,
        nickname = handle,
        name = null,
        birthDate = null,
        phoneNumber = null,
        gender = null,
        birthYear = null,
        mainLocationText = null,
        mainLocationGeom = null,
        profileImageUrl = null,
    )
}
