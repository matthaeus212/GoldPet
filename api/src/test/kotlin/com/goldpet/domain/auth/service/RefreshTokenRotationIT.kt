package com.goldpet.domain.auth.service

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.auth.entity.RefreshTokenRevokeReason
import com.goldpet.domain.auth.repository.UserRefreshTokenRepository
import com.goldpet.domain.common.exception.UnauthorizedException
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * V66 통합 테스트 — 실 토큰 발급 → rotate → reuse 흐름.
 * 2026-07 보강: 5초 grace window 는 parent 체인 walk 정책으로 대체됨 (경과 시간과 무관하게
 * 체인이 살아있는지만 본다). 관련 시나리오도 함께 갱신.
 *
 * 실행 전: `./deploy-local/scripts/start.sh` 로 local PostGIS(5433) 기동.
 * 6 시나리오:
 *  1. issueInitial — DB row 생성 (parent=NULL)
 *  2. 정상 회전 — old ROTATED revoke + new chain link 생성 (parentHash 설정)
 *  3. legacy compat — DB row 없는 token 회전 시 새 chain 시작
 *  4. stale-generation (1-hop) — ROTATED-revoke 된 토큰의 직계 head 가 살아있으면 시간 경과와
 *     무관하게 REUSE 면제 + branch token 발급 (head 는 revoke 되지 않음)
 *  5. multiple children at an intermediate hop (real DB) — 한 부모가 자식을 3개 이상 갖는
 *     상태(branch 발급 반복)에서 조상 재생 시 `findByParentTokenHash` 가 List 를 반환하지 않으면
 *     `IncorrectResultSizeDataAccessException`(500) 이 나던 HIGH 회귀의 실제 DB 재현 가드
 *  6. reuse detected — 체인이 dead-end(더 이상 후속 회전 없음)인 revoked token 재사용 → device chain 전체 REUSE_DETECTED
 */
class RefreshTokenRotationIT : IntegrationTestBase() {

    @Autowired private lateinit var rotationService: RefreshTokenRotationService
    @Autowired private lateinit var tokenRepo: UserRefreshTokenRepository
    @Autowired private lateinit var jwt: JwtTokenProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var testUser: User
    private val deviceA = "v66-it-device-A"
    private val deviceB = "v66-it-device-B"

    /**
     * `revokeAllForDevice` 는 `@Modifying` 벌크 쿼리라 활성 트랜잭션이 필요하다.
     * 테스트 메서드 전체를 `@Transactional` 로 감싸면 `IntegrationTestBase.clearAdvisoryLocks()` 가
     * `@BeforeEach` 에서 pool 의 모든 connection 을 직접 선점하려다 (테스트 tx 가 이미 하나를 쥔 채) 고갈돼
     * timeout 난다. 그래서 이 호출 하나만 짧은 별도 tx 로 즉시 commit 한다.
     */
    private fun revokeAllForDeviceCommitted(userId: Long, deviceId: String, reason: RefreshTokenRevokeReason) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            tokenRepo.revokeAllForDevice(userId, deviceId, LocalDateTime.now(), reason)
        }
    }

    @BeforeEach
    fun seedTokens() {
        // 이전 IT 잔존 row 제거 (deviceA/deviceB 한정)
        tokenRepo.findAll()
            .filter { it.deviceId == deviceA || it.deviceId == deviceB }
            .forEach { tokenRepo.deleteById(it.id) }
        testUser = userRepository.findAll().firstOrNull()
            ?: error("Test DB must seed at least one user")
    }

    @Test
    fun `issueInitial persists row with parent=NULL`() {
        val token = rotationService.issueInitial(testUser.id, deviceA)
        assertNotNull(token)

        val active = tokenRepo.findActiveByUserAndDevice(testUser.id, deviceA, LocalDateTime.now())
        assertEquals(1, active.size, "issueInitial must create exactly 1 active row")
        assertNull(active[0].parentTokenHash, "Initial row must have parent=NULL")
        assertNull(active[0].revokedAt)
    }

    @Test
    fun `normal rotation revokes parent and links new row via parent_token_hash`() {
        val initial = rotationService.issueInitial(testUser.id, deviceA)
        val rotated = rotationService.rotate(initial, deviceA)
        assertNotEquals(initial, rotated.newRefreshToken, "Rotation must yield a different token")

        val rows = tokenRepo.findAll().filter { it.userId == testUser.id && it.deviceId == deviceA }
        assertEquals(2, rows.size, "After rotation: 1 revoked + 1 active = 2 rows for device")

        val revoked = rows.find { it.revokedAt != null }
        val active = rows.find { it.revokedAt == null }
        assertNotNull(revoked, "Parent must be revoked")
        assertNotNull(active, "New token row must exist active")
        assertEquals(RefreshTokenRevokeReason.ROTATED, revoked!!.revokeReason)
        assertNotNull(active!!.parentTokenHash, "New row must carry parent_token_hash for chain")
        assertEquals(revoked.tokenHash, active.parentTokenHash, "parent_token_hash must match revoked token's hash")
    }

    @Test
    fun `legacy compat — rotating a token with no DB row creates a fresh chain`() {
        // pre-V66 token 시뮬레이션 — issueInitial 호출 없이 직접 JWT 발급
        val legacyToken = jwt.generateRefreshToken(testUser.id)
        val result = rotationService.rotate(legacyToken, deviceA)
        assertNotNull(result.newRefreshToken)

        val rows = tokenRepo.findAll().filter { it.userId == testUser.id && it.deviceId == deviceA }
        assertEquals(1, rows.size, "Legacy compat must create exactly 1 new chain row")
        assertNull(rows[0].parentTokenHash, "Legacy compat chain starts with parent=NULL")
    }

    @Test
    fun `stale-generation (1-hop) replay is tolerated regardless of elapsed time — dual-client desync`() {
        val initial = rotationService.issueInitial(testUser.id, deviceA)
        val firstRotation = rotationService.rotate(initial, deviceA)

        // 시간 경과와 무관함을 증명하기 위해 revoke 된 initial 행을 1시간 전으로 backdate.
        val revokedInitial = tokenRepo.findAll().first { it.deviceId == deviceA && it.revokedAt != null }
        revokedInitial.revokedAt = LocalDateTime.now().minusHours(1)
        tokenRepo.save(revokedInitial)

        // 다른 클라이언트(예: React WebView)가 여전히 구세대 `initial` 토큰으로 재요청 —
        // 체인이 살아있으므로(1-hop) 시간과 무관하게 REUSE 가 아닌 branch 발급으로 처리되어야 한다.
        val secondRotation = rotationService.rotate(initial, deviceA)
        assertNotNull(secondRotation.newRefreshToken)
        assertNotEquals(firstRotation.newRefreshToken, secondRotation.newRefreshToken)

        // REUSE_DETECTED 미발동 검증
        val anyReuse = tokenRepo.findAll().any { it.deviceId == deviceA && it.revokeReason == RefreshTokenRevokeReason.REUSE_DETECTED }
        assertEquals(false, anyReuse, "1-hop stale-generation replay must NOT trigger REUSE_DETECTED even after time elapses")

        // head(firstRotation 결과)는 revoke 되지 않고, branch(secondRotation 결과)와 함께 2개 활성 상태여야 한다.
        val activeRows = tokenRepo.findActiveByUserAndDevice(testUser.id, deviceA, LocalDateTime.now())
        assertEquals(2, activeRows.size, "head 는 그대로 유지되고 branch 가 추가되어 활성 토큰이 2개여야 한다")
    }

    /**
     * HIGH 회귀 가드 — 실 DB 레벨 재현. `findByParentTokenHash` 가 Optional(단건) 이던 시절엔
     * 한 부모가 자식을 2개 이상 가지면 `IncorrectResultSizeDataAccessException` 이 터져 rotate()
     * 가 401 대신 500 으로 깨졌다. branch 발급이 반복되면 정확히 이 상황(한 부모에 자식 3개 이상)이
     * 실제 운영에서도 만들어진다 — 여기서는 mock 이 아닌 실제 Postgres 위에서 재현한다.
     *
     * 재현 절차 (리뷰에서 지적된 그대로):
     *   t1 -rotate-> t2(head) / 구세대 t1 을 2번 재생 → b1, b2 (parent=t2, t2 는 계속 살아있음)
     *   / t2 정상 회전 → b3 (t2 도 이제 ROTATED-revoke, 그 결과 t2 의 자식이 b1/b2/b3 3개)
     *   / 구세대 t1 재생 → walk 이 t1→t2→(t2 의 자식 3행) 을 만난다 — 500 이 아니라 살아있는
     *   sibling(b1/b2/b3 중 하나) 으로 branch 발급이 성공해야 한다.
     */
    @Test
    fun `multiple children at an intermediate hop (real DB) — ancestor replay tolerates without 500`() {
        val t1 = rotationService.issueInitial(testUser.id, deviceA)
        val t2 = rotationService.rotate(t1, deviceA).newRefreshToken

        // 구세대 t1 을 2번 재생 — 매번 살아있는 head(t2) 로 chain walk 이 성공해 branch(b1, b2) 발급.
        // t2 는 branch 발급 경로에서는 revoke 되지 않으므로 두 번 다 성공해야 한다.
        val b1 = rotationService.rotate(t1, deviceA).newRefreshToken
        val b2 = rotationService.rotate(t1, deviceA).newRefreshToken
        assertNotEquals(b1, b2)

        // t2 정상 회전 → t2 ROTATED-revoke, b3 발급. 이제 t2 의 자식이 b1/b2/b3 3개.
        rotationService.rotate(t2, deviceA)

        // 회귀 재현의 전제 조건: t1, t2 는 revoked, b1/b2/b3 는 active — 총 5행, 활성 3행.
        val rowsBefore = tokenRepo.findAll().filter { it.userId == testUser.id && it.deviceId == deviceA }
        assertEquals(5, rowsBefore.size, "t1, t2 (revoked) + b1, b2, b3 (active) must exist before the final replay")
        assertEquals(3, rowsBefore.count { it.revokedAt == null }, "b1, b2, b3 must all be active — t2 must have 3 children")

        // 구세대 t1 재생 — walk 이 t1 -> t2(dead, ROTATED) -> t2 의 자식 3행을 만난다.
        // Optional 기반 구현이면 여기서 IncorrectResultSizeDataAccessException(500). 지금은 성공해야 한다.
        val finalResult = rotationService.rotate(t1, deviceA)
        assertNotNull(finalResult.newRefreshToken)

        // branch(b4) 가 b1/b2/b3 중 하나를 parent 로 추가 발급되어 활성 토큰이 4개가 되어야 한다.
        val activeAfter = tokenRepo.findActiveByUserAndDevice(testUser.id, deviceA, LocalDateTime.now())
        assertEquals(4, activeAfter.size, "a 4th branch must be issued off one of the live siblings, no 500")
    }

    @Test
    fun `reuse detection — replaying a token from a dead chain revokes the whole device (deviceA only)`() {
        val tokenA = rotationService.issueInitial(testUser.id, deviceA)
        val tokenB = rotationService.issueInitial(testUser.id, deviceB)
        val rotatedA = rotationService.rotate(tokenA, deviceA)
        assertNotNull(rotatedA.newRefreshToken)

        // deviceA 의 현재 head(rotatedA 로 생성된 토큰)를 로그아웃으로 완전히 dead-end 처리 —
        // 이후 이 chain 에는 더 이상 회전 기록이 없다.
        revokeAllForDeviceCommitted(testUser.id, deviceA, RefreshTokenRevokeReason.LOGOUT)

        // 로그아웃 후 동일 device 에서 재로그인 — parent=NULL 인 완전히 새로운 세션.
        val reLoginToken = rotationService.issueInitial(testUser.id, deviceA)
        assertNotNull(reLoginToken)

        // 로그아웃 이전의 tokenA(ROTATED-revoked) 를 재생 시도 — tokenA 의 체인은 dead end 이므로
        // REUSE_DETECTED 가 발동해 deviceA 의 모든 활성 세션(재로그인 세션 포함)을 revoke 한다.
        assertThrows<UnauthorizedException> {
            rotationService.rotate(tokenA, deviceA)
        }

        // deviceA 전체 revoke 검증 (재로그인 세션 포함 — device-wide 차단은 의도된 동작)
        val activeA = tokenRepo.findActiveByUserAndDevice(testUser.id, deviceA, LocalDateTime.now())
        assertEquals(0, activeA.size, "deviceA chain must be fully revoked, including the re-login session")
        val reuseRow = tokenRepo.findAll().any { it.deviceId == deviceA && it.revokeReason == RefreshTokenRevokeReason.REUSE_DETECTED }
        assertTrue(reuseRow, "REUSE_DETECTED must be recorded for deviceA")

        // deviceB 영향 없음 검증
        val activeB = tokenRepo.findActiveByUserAndDevice(testUser.id, deviceB, LocalDateTime.now())
        assertEquals(1, activeB.size, "deviceB chain must remain active (other device unaffected)")
    }
}
