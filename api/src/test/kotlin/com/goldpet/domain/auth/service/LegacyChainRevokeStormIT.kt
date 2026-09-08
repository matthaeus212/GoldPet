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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * BLOCKER #2 (BE) — `legacy-{userId}` cross-device revoke **storm** 결정적 재현 IT.
 *
 * 배경:
 *   클라이언트가 `X-Device-Id` 헤더를 보내지 않으면 [RefreshTokenRotationService.rotate] 의
 *   `resolvedDeviceId = deviceId?.takeIf { it.isNotBlank() } ?: "legacy-$userId"` 분기에 의해
 *   **한 유저의 모든 기기 토큰이 단일 `legacy-{userId}` chain 으로 붕괴**한다.
 *   이 상태에서 한 기기가 revoked 된 토큰을 재사용하면 reuse-detection 이
 *   `revokeAllForDevice(legacy-{userId})` 로 **해당 유저의 모든 기기 토큰을 한꺼번에 revoke** —
 *   즉, 무고한 다른 기기까지 강제 로그아웃되는 "storm" 이 발생할 수 있다.
 *
 * 2026-07 보강 — parent 체인 walk 관용 정책 도입 이후 이 파일의 시나리오는 세 가지로 나뉜다.
 *   1. STORM 완화 (deviceId 미전송, 체인 생존): legacy bucket 이 붕괴해도 체인이 살아있으면
 *      더 이상 storm 이 아니라 stale-generation 관용(branch 발급)으로 처리된다 — 정책 변경의 부수 효과.
 *   2. STORM 잔존 (deviceId 미전송, 체인 사망): legacy bucket 의 head 가 로그아웃 등으로 완전히
 *      죽으면, 해당 bucket 재사용은 여전히 bucket 전체(무고한 다른 기기 포함)를 revoke 한다 —
 *      `legacy-{userId}` 붕괴 자체는 이번 작업 범위 밖의 별개 이슈이며 아직 해결되지 않았음을 확인하는 가드.
 *   3. ISOLATION (deviceId 분리 전송): 기기별 독립 chain → 한 기기의 dead-chain reuse revoke 가
 *      다른 기기에 무영향 (수정안 PASS 조건).
 *
 * 실행 전: `./deploy-local/scripts/start.sh` 로 local PostGIS(5433) 기동.
 */
@Tag("integration")
class LegacyChainRevokeStormIT : IntegrationTestBase() {

    @Autowired private lateinit var rotationService: RefreshTokenRotationService
    @Autowired private lateinit var tokenRepo: UserRefreshTokenRepository
    @Autowired private lateinit var jwt: JwtTokenProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var testUser: User
    private lateinit var legacyDeviceId: String
    private val deviceA = "storm-it-device-A"
    private val deviceB = "storm-it-device-B"

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
    fun seed() {
        testUser = userRepository.findAll().firstOrNull()
            ?: error("Test DB must seed at least one user")
        legacyDeviceId = "legacy-${testUser.id}"

        // 이전 IT 잔존 row 제거 — 본 테스트가 다루는 3개 device bucket 한정.
        tokenRepo.findAll()
            .filter { it.deviceId == legacyDeviceId || it.deviceId == deviceA || it.deviceId == deviceB }
            .forEach { tokenRepo.deleteById(it.id) }
    }

    /**
     * STORM 완화 — deviceId 미전송으로 legacy bucket 이 붕괴하더라도, 체인이 살아있는 한
     * (즉 앞선 rotate 로 생긴 head 가 아직 active 라면) 더 이상 storm 이 아니라
     * stale-generation 관용 정책(branch 발급)으로 처리된다.
     *
     * 시나리오:
     *   - 기기 A: legacy(raw) 토큰을 deviceId 없이 rotate → t1 (legacy bucket DB row 생성).
     *   - 기기 B: 같은 t1 을 deviceId 없이 rotate → t2 (t1 은 ROTATED revoke, t2 가 legacy bucket 활성/head).
     *   - 기기 A: 이미 revoke 된 t1 을 deviceId 없이 재-rotate → chain walk 이 1-hop 만에
     *     살아있는 head(t2) 를 찾아 REUSE 가 아닌 branch 발급(t3) 으로 처리 — t2 는 revoke 되지 않는다.
     */
    @Test
    fun `STORM 완화 — legacy bucket 체인이 살아있으면 재생은 storm 대신 stale-generation 으로 관용된다`() {
        // 기기 A: deviceId 없이 첫 rotate → legacy compat 경로로 legacy bucket DB row 생성.
        val rawLegacyToken = jwt.generateRefreshToken(testUser.id)
        val t1 = rotationService.rotate(rawLegacyToken, deviceId = null).newRefreshToken
        assertNotNull(t1)

        // 모든 토큰이 legacy-{userId} bucket 으로 붕괴했는지 확인.
        val afterT1 = tokenRepo.findActiveByUserAndDevice(testUser.id, legacyDeviceId, LocalDateTime.now())
        assertEquals(1, afterT1.size, "deviceId 미전송 토큰은 legacy-{userId} bucket 에 적재되어야 한다")

        // 기기 B: 같은 t1 을 deviceId 없이 rotate → 정상 회전(t1 ROTATED revoke, t2 활성/head).
        val t2 = rotationService.rotate(t1, deviceId = null).newRefreshToken
        assertNotEquals(t1, t2, "회전은 새 토큰을 발급해야 한다")

        val activeBeforeReplay = tokenRepo.findActiveByUserAndDevice(testUser.id, legacyDeviceId, LocalDateTime.now())
        assertEquals(1, activeBeforeReplay.size, "회전 후 legacy bucket 활성 토큰은 t2 하나여야 한다")

        // 기기 A: 이미 ROTATED-revoke 된 t1 재사용 — head(t2) 가 살아있으므로 chain walk 이
        // 1-hop 만에 head 를 찾아 reuse 가 아닌 branch 발급으로 처리되어야 한다 (예외를 던지면 이 호출에서 바로 실패).
        val t3 = rotationService.rotate(t1, deviceId = null)
        assertNotNull(t3.newRefreshToken)

        // storm 미발생 검증 — head(t2) 는 그대로 살아있고 branch(t3) 가 추가되어 활성 토큰이 2개여야 한다.
        val activeAfterReplay = tokenRepo.findActiveByUserAndDevice(testUser.id, legacyDeviceId, LocalDateTime.now())
        assertEquals(2, activeAfterReplay.size, "head 는 revoke 되지 않고 branch 가 추가되어 2개 활성이어야 한다")
        val anyReuse = tokenRepo.findAll().any {
            it.deviceId == legacyDeviceId && it.revokeReason == RefreshTokenRevokeReason.REUSE_DETECTED
        }
        assertEquals(false, anyReuse, "체인이 살아있는 replay 는 더 이상 REUSE_DETECTED 를 유발하지 않는다")
    }

    /**
     * STORM 잔존 — legacy bucket 의 head 가 로그아웃 등으로 완전히 dead-end 되면,
     * 그 이후의 replay 는 여전히 bucket 전체(무고한 다른 기기 포함)를 revoke 한다.
     * `legacy-{userId}` bucket 붕괴 자체(기기 구분 불가)는 이번 작업 범위 밖의 별개 이슈이며,
     * 아직 해결되지 않았음을 알리는 회귀 가드.
     */
    @Test
    fun `STORM 잔존 — legacy bucket head 가 완전히 죽으면 재생 시 bucket 전체(다른 기기 포함)가 revoke 된다`() {
        val rawLegacyToken = jwt.generateRefreshToken(testUser.id)
        val t1 = rotationService.rotate(rawLegacyToken, deviceId = null).newRefreshToken
        val t2 = rotationService.rotate(t1, deviceId = null).newRefreshToken
        assertNotEquals(t1, t2)

        // legacy bucket 의 현재 head(t2) 를 로그아웃으로 완전히 dead-end 처리.
        revokeAllForDeviceCommitted(testUser.id, legacyDeviceId, RefreshTokenRevokeReason.LOGOUT)

        // 로그아웃 후 같은 bucket(deviceId 미전송) 으로 재로그인 — parent=NULL 인 새 세션(무고한 기기 B 시뮬레이션).
        val reLoginToken = rotationService.issueInitial(testUser.id, legacyDeviceId)
        assertNotNull(reLoginToken)

        // 기기 A: 이미 revoke 된 t1 재사용 — t1 -> t2 체인이 dead end 이므로 reuse-detection 발동,
        // legacy bucket 전체(재로그인 세션 포함)가 revoke 된다.
        assertThrows<UnauthorizedException> {
            rotationService.rotate(t1, deviceId = null)
        }

        val activeAfterStorm = tokenRepo.findActiveByUserAndDevice(testUser.id, legacyDeviceId, LocalDateTime.now())
        assertEquals(
            0, activeAfterStorm.size,
            "dead chain 재생은 여전히 legacy bucket 전체(재로그인 세션 포함)를 revoke 해야 한다",
        )
        val reuseRecorded = tokenRepo.findAll().any {
            it.deviceId == legacyDeviceId && it.revokeReason == RefreshTokenRevokeReason.REUSE_DETECTED
        }
        assertTrue(reuseRecorded, "REUSE_DETECTED 가 legacy bucket 에 기록되어야 한다")
    }

    /**
     * ISOLATION 대조 — deviceId 를 분리 전송하면 기기별 독립 chain 이 유지되어
     * 한 기기의 dead-chain reuse-detection revoke 가 다른 기기에 영향을 주지 않는다 (storm 미발생).
     */
    @Test
    fun `ISOLATION — deviceId 분리 전송 시 기기 A 의 dead-chain reuse revoke 가 기기 B 에 무영향`() {
        // 기기 A·B 각각 독립 chain 으로 초기 발급.
        val tA1 = rotationService.issueInitial(testUser.id, deviceA)
        val tB1 = rotationService.issueInitial(testUser.id, deviceB)
        assertNotNull(tB1)

        // 기기 A 정상 회전 → tA1 revoke, tA2 활성(head).
        rotationService.rotate(tA1, deviceA)

        // 기기 A 의 head(tA2) 를 로그아웃으로 완전히 dead-end 처리 — 이후 이 chain 에는 회전 기록이 없다.
        revokeAllForDeviceCommitted(testUser.id, deviceA, RefreshTokenRevokeReason.LOGOUT)

        // 기기 A 에서 재로그인 — parent=NULL 인 새 세션.
        val reLoginA = rotationService.issueInitial(testUser.id, deviceA)
        assertNotNull(reLoginA)

        // 기기 A: revoke 된 tA1 재사용 — tA1 -> tA2 체인이 dead end 이므로 reuse-detection 발동.
        assertThrows<UnauthorizedException> {
            rotationService.rotate(tA1, deviceA)
        }

        // 기기 A chain 전멸 검증 (재로그인 세션 포함 — device-wide 차단은 의도된 동작).
        val activeA = tokenRepo.findActiveByUserAndDevice(testUser.id, deviceA, LocalDateTime.now())
        assertEquals(0, activeA.size, "기기 A chain 은 reuse-detection 으로 전부 revoke 되어야 한다")

        // 기기 B 무영향 검증 — storm 미발생.
        val activeB = tokenRepo.findActiveByUserAndDevice(testUser.id, deviceB, LocalDateTime.now())
        assertEquals(
            1, activeB.size,
            "ISOLATION: deviceId 분리 시 기기 A 의 revoke 가 기기 B 활성 토큰에 영향을 주면 안 된다",
        )
    }
}
