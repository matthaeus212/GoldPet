package com.goldpet.domain.auth.service

import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.auth.entity.RefreshTokenRevokeReason
import com.goldpet.domain.auth.entity.UserRefreshToken
import com.goldpet.domain.auth.repository.UserRefreshTokenRepository
import com.goldpet.domain.common.exception.UnauthorizedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

class RefreshTokenRotationServiceTest {

    @Mock private lateinit var repo: UserRefreshTokenRepository
    @Mock private lateinit var jwt: JwtTokenProvider

    private lateinit var service: RefreshTokenRotationService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = RefreshTokenRotationService(repo, jwt)
        // default: 토큰 검증/파싱 stub
        whenever(jwt.validateRefreshToken(any())).thenReturn(true)
        whenever(jwt.getUserIdFromRefreshToken(any())).thenReturn(100L)
        whenever(jwt.generateRefreshToken(any())).thenReturn("new-jwt-token")
        whenever(jwt.getRefreshExpiry(any())).thenReturn(LocalDateTime.now().plusDays(30))
    }

    private fun row(
        userId: Long = 100L,
        deviceId: String = "device-A",
        tokenHash: String = "OLD_HASH",
        parentHash: String? = null,
        revokedAt: LocalDateTime? = null,
        revokeReason: RefreshTokenRevokeReason? = null,
        issuedAt: LocalDateTime = LocalDateTime.now().minusMinutes(1),
    ) = UserRefreshToken(
        userId = userId,
        deviceId = deviceId,
        tokenHash = tokenHash,
        parentTokenHash = parentHash,
        issuedAt = issuedAt,
        expiresAt = LocalDateTime.now().plusDays(30),
        revokedAt = revokedAt,
    ).apply { this.revokeReason = revokeReason }

    @Test
    fun `legacy compat — no DB row creates new chain (parent=NULL)`() {
        whenever(repo.findByTokenHash(any())).thenReturn(Optional.empty())

        val result = service.rotate(oldToken = "any.jwt.token", deviceId = "device-A")

        assertEquals("new-jwt-token", result.newRefreshToken)
        assertEquals(100L, result.userId)

        val saved = ArgumentCaptor.forClass(UserRefreshToken::class.java)
        verify(repo).save(saved.capture())
        assertEquals("device-A", saved.value.deviceId)
        assertEquals(null, saved.value.parentTokenHash, "Legacy compat must persist parent=NULL")
        verify(repo, never()).revokeByTokenHash(any(), any(), any())
        verify(repo, never()).revokeAllForDevice(any(), any(), any(), any())
    }

    @Test
    fun `normal rotation — revoked=false revokes parent and inserts new chain link`() {
        val existing = row(revokedAt = null)
        whenever(repo.findByTokenHash(any())).thenReturn(Optional.of(existing))

        val result = service.rotate(oldToken = "valid.jwt", deviceId = "device-A")

        assertEquals("new-jwt-token", result.newRefreshToken)
        verify(repo).revokeByTokenHash(any(), any(), eq(RefreshTokenRevokeReason.ROTATED))

        val saved = ArgumentCaptor.forClass(UserRefreshToken::class.java)
        verify(repo).save(saved.capture())
        assertNotNull(saved.value.parentTokenHash, "New row must carry parent_token_hash for chain tracking")
        assertEquals("device-A", saved.value.deviceId)
        verify(repo, never()).revokeAllForDevice(any(), any(), any(), any())
    }

    // (a) 1세대 전 ROTATED 토큰 — chain walk 1-hop 만에 살아있는 head 도달 → 새 토큰 발급, head 는 비revoke.
    @Test
    fun `stale-generation (1-hop) — ROTATED token whose immediate child is alive issues a branch token without revoking head`() {
        val head = row(tokenHash = "HEAD_HASH", parentHash = "OLD_HASH", revokedAt = null)
        val existing = row(
            tokenHash = "OLD_HASH",
            revokedAt = LocalDateTime.now().minusSeconds(2),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        whenever(repo.findByTokenHash(any())).thenReturn(Optional.of(existing))
        whenever(repo.findByParentTokenHash(eq("OLD_HASH"))).thenReturn(listOf(head))

        val result = service.rotate(oldToken = "valid.jwt", deviceId = "device-A")

        assertEquals("new-jwt-token", result.newRefreshToken)
        val saved = ArgumentCaptor.forClass(UserRefreshToken::class.java)
        verify(repo).save(saved.capture())
        assertEquals("HEAD_HASH", saved.value.parentTokenHash, "New branch must chain off the alive head")
        verify(repo, never()).revokeByTokenHash(any(), any(), any()) // head must NOT be revoked
        verify(repo, never()).revokeAllForDevice(any(), any(), any(), any())
    }

    // (b) 2세대 이상 전 — 모든 hop 이 ROTATED 인 채로 이어지다가 결국 살아있는 head 에 도달 → 새 토큰 발급.
    @Test
    fun `stale-generation (multi-hop) — chain of ROTATED hops leading to an alive head issues a branch token`() {
        val head = row(tokenHash = "GEN2_HASH", parentHash = "GEN1_HASH", revokedAt = null)
        val gen1 = row(
            tokenHash = "GEN1_HASH",
            parentHash = "OLD_HASH",
            revokedAt = LocalDateTime.now().minusSeconds(10),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        val existing = row(
            tokenHash = "OLD_HASH",
            revokedAt = LocalDateTime.now().minusSeconds(20),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        whenever(repo.findByTokenHash(any())).thenReturn(Optional.of(existing))
        whenever(repo.findByParentTokenHash(eq("OLD_HASH"))).thenReturn(listOf(gen1))
        whenever(repo.findByParentTokenHash(eq("GEN1_HASH"))).thenReturn(listOf(head))

        val result = service.rotate(oldToken = "valid.jwt", deviceId = "device-A")

        assertEquals("new-jwt-token", result.newRefreshToken)
        val saved = ArgumentCaptor.forClass(UserRefreshToken::class.java)
        verify(repo).save(saved.capture())
        assertEquals("GEN2_HASH", saved.value.parentTokenHash, "Branch must chain off the deepest alive head")
        verify(repo, never()).revokeAllForDevice(any(), any(), any(), any())
    }

    // (c) 죽은 체인 — presented token 은 ROTATED 지만, 후속 회전 기록이 없는 dead end → REUSE_DETECTED.
    @Test
    fun `dead chain — ROTATED token whose descendant chain dead-ends triggers REUSE_DETECTED`() {
        val existing = row(
            tokenHash = "OLD_HASH",
            revokedAt = LocalDateTime.now().minusMinutes(2),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        whenever(repo.findByTokenHash(any())).thenReturn(Optional.of(existing))
        whenever(repo.findByParentTokenHash(any())).thenReturn(emptyList()) // no further rotation ever recorded

        val ex = assertThrows<UnauthorizedException> {
            service.rotate(oldToken = "stolen.jwt", deviceId = "device-A")
        }
        assert(ex.message!!.contains("reuse"))

        verify(repo).revokeAllForDevice(
            eq(100L), eq("device-A"), any(),
            eq(RefreshTokenRevokeReason.REUSE_DETECTED),
        )
        verify(repo, never()).save(any())
    }

    // (d) LOGOUT 으로 revoke 된 토큰 — revokeReason 이 ROTATED 가 아니므로 chain walk 자체를 타지 않고 즉시 REUSE_DETECTED.
    @Test
    fun `LOGOUT-revoked token is rejected immediately without chain walk`() {
        val existing = row(
            revokedAt = LocalDateTime.now().minusMinutes(1),
            revokeReason = RefreshTokenRevokeReason.LOGOUT,
        )
        whenever(repo.findByTokenHash(any())).thenReturn(Optional.of(existing))

        assertThrows<UnauthorizedException> {
            service.rotate(oldToken = "logged-out.jwt", deviceId = "device-A")
        }

        verify(repo, never()).findByParentTokenHash(any()) // gate fails before any chain walk
        verify(repo).revokeAllForDevice(
            eq(100L), eq("device-A"), any(),
            eq(RefreshTokenRevokeReason.REUSE_DETECTED),
        )
    }

    // (e) hop 중 deviceId 불일치 — 유일한 자식이 다른 기기라 유효 자식이 없는 것으로 간주해 즉시 REUSE_DETECTED.
    @Test
    fun `device mismatch during chain walk triggers REUSE_DETECTED even if the mismatched hop is alive`() {
        val existing = row(
            tokenHash = "OLD_HASH",
            deviceId = "device-A",
            revokedAt = LocalDateTime.now().minusSeconds(2),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        val mismatchedChild = row(
            tokenHash = "CHILD_HASH",
            parentHash = "OLD_HASH",
            deviceId = "device-B",
            revokedAt = null,
        )
        whenever(repo.findByTokenHash(any())).thenReturn(Optional.of(existing))
        whenever(repo.findByParentTokenHash(eq("OLD_HASH"))).thenReturn(listOf(mismatchedChild))

        assertThrows<UnauthorizedException> {
            service.rotate(oldToken = "stolen.jwt", deviceId = "device-A")
        }

        verify(repo).revokeAllForDevice(
            eq(100L), eq("device-A"), any(),
            eq(RefreshTokenRevokeReason.REUSE_DETECTED),
        )
        verify(repo, never()).save(any())
    }

    // (f) hop 상한 초과 — 무한/초장기 체인을 시뮬레이션(항상 revoked·ROTATED 인 동일 child 반복 반환)해도
    // 무한루프 없이 상한에서 REUSE_DETECTED 로 종료.
    @Test
    fun `chain walk exceeding the hop cap terminates safely with REUSE_DETECTED`() {
        val existing = row(
            tokenHash = "OLD_HASH",
            revokedAt = LocalDateTime.now().minusMinutes(30),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        val alwaysRevokedChild = row(
            tokenHash = "LOOP_HASH",
            parentHash = "LOOP_HASH",
            revokedAt = LocalDateTime.now().minusMinutes(29),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        whenever(repo.findByTokenHash(any())).thenReturn(Optional.of(existing))
        whenever(repo.findByParentTokenHash(any())).thenReturn(listOf(alwaysRevokedChild))

        assertThrows<UnauthorizedException> {
            service.rotate(oldToken = "stolen.jwt", deviceId = "device-A")
        }

        verify(repo, times(RefreshTokenRotationService.MAX_CHAIN_WALK_HOPS)).findByParentTokenHash(any())
        verify(repo).revokeAllForDevice(
            eq(100L), eq("device-A"), any(),
            eq(RefreshTokenRevokeReason.REUSE_DETECTED),
        )
    }

    // (g) HIGH 회귀 가드 — 중간 노드가 복수 자식(branch 발급 반복으로 생성)을 가진 상태에서 조상 토큰을
    // 재생해도 findByParentTokenHash 가 여러 행을 반환한다고 500 이 나면 안 되고, 살아있는 자식을 찾아 관용해야 한다.
    // 재현: t1(existing) -ROTATED-> t2(revoked, ROTATED) 의 자식이 b1(alive), b2(alive), b3(alive) 3개.
    @Test
    fun `multiple children at an intermediate hop — ancestor replay tolerates via a live sibling without 500`() {
        val existing = row(
            tokenHash = "T1_HASH",
            revokedAt = LocalDateTime.now().minusMinutes(5),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        val t2 = row(
            tokenHash = "T2_HASH",
            parentHash = "T1_HASH",
            revokedAt = LocalDateTime.now().minusMinutes(3),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        val b1 = row(tokenHash = "B1_HASH", parentHash = "T2_HASH", revokedAt = null)
        val b2 = row(tokenHash = "B2_HASH", parentHash = "T2_HASH", revokedAt = null)
        val b3 = row(tokenHash = "B3_HASH", parentHash = "T2_HASH", revokedAt = null)

        whenever(repo.findByTokenHash(any())).thenReturn(Optional.of(existing))
        whenever(repo.findByParentTokenHash(eq("T1_HASH"))).thenReturn(listOf(t2))
        // 핵심: 한 부모(t2)에 자식이 3개 — 예전 Optional 기반 구현이면 IncorrectResultSizeDataAccessException.
        whenever(repo.findByParentTokenHash(eq("T2_HASH"))).thenReturn(listOf(b1, b2, b3))

        val result = service.rotate(oldToken = "stale-ancestor.jwt", deviceId = "device-A")

        assertEquals("new-jwt-token", result.newRefreshToken)
        val saved = ArgumentCaptor.forClass(UserRefreshToken::class.java)
        verify(repo).save(saved.capture())
        assertTrue(
            saved.value.parentTokenHash in setOf("B1_HASH", "B2_HASH", "B3_HASH"),
            "Branch must chain off one of the live siblings, got ${saved.value.parentTokenHash}",
        )
        verify(repo, never()).revokeAllForDevice(any(), any(), any(), any())
    }

    // (h) 복수 자식이 있어도 전부 죽은 체인(비-ROTATED 종단, 활성 없음)이면 REUSE_DETECTED.
    @Test
    fun `multiple children at an intermediate hop — all dead ends still trigger REUSE_DETECTED`() {
        val existing = row(
            tokenHash = "T1_HASH",
            revokedAt = LocalDateTime.now().minusMinutes(5),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        val c1 = row(
            tokenHash = "C1_HASH",
            parentHash = "T1_HASH",
            revokedAt = LocalDateTime.now().minusMinutes(3),
            revokeReason = RefreshTokenRevokeReason.ROTATED,
        )
        // c1 의 자식들 — 전부 활성도 아니고 ROTATED 도 아님(LOGOUT/REUSE_DETECTED 종단) → 더 내려갈 곳 없음.
        val d1 = row(
            tokenHash = "D1_HASH",
            parentHash = "C1_HASH",
            revokedAt = LocalDateTime.now().minusMinutes(1),
            revokeReason = RefreshTokenRevokeReason.LOGOUT,
        )
        val d2 = row(
            tokenHash = "D2_HASH",
            parentHash = "C1_HASH",
            revokedAt = LocalDateTime.now().minusMinutes(1),
            revokeReason = RefreshTokenRevokeReason.REUSE_DETECTED,
        )

        whenever(repo.findByTokenHash(any())).thenReturn(Optional.of(existing))
        whenever(repo.findByParentTokenHash(eq("T1_HASH"))).thenReturn(listOf(c1))
        whenever(repo.findByParentTokenHash(eq("C1_HASH"))).thenReturn(listOf(d1, d2))

        assertThrows<UnauthorizedException> {
            service.rotate(oldToken = "stolen.jwt", deviceId = "device-A")
        }

        verify(repo).revokeAllForDevice(
            eq(100L), eq("device-A"), any(),
            eq(RefreshTokenRevokeReason.REUSE_DETECTED),
        )
        verify(repo, never()).save(any())
    }

    @Test
    fun `invalid signature throws Unauthorized without DB access`() {
        whenever(jwt.validateRefreshToken(any())).thenReturn(false)

        assertThrows<UnauthorizedException> {
            service.rotate(oldToken = "tampered.jwt", deviceId = "device-A")
        }
        verify(repo, never()).findByTokenHash(any())
    }

    @Test
    fun `issueInitial persists new row with parent=NULL`() {
        val token = service.issueInitial(userId = 200L, deviceId = "device-B")
        assertEquals("new-jwt-token", token)

        val saved = ArgumentCaptor.forClass(UserRefreshToken::class.java)
        verify(repo).save(saved.capture())
        assertEquals(200L, saved.value.userId)
        assertEquals("device-B", saved.value.deviceId)
        assertEquals(null, saved.value.parentTokenHash)
    }

    @Test
    fun `null deviceId falls back to legacy-userId pattern`() {
        whenever(repo.findByTokenHash(any())).thenReturn(Optional.empty())

        service.rotate(oldToken = "valid.jwt", deviceId = null)

        val saved = ArgumentCaptor.forClass(UserRefreshToken::class.java)
        verify(repo).save(saved.capture())
        assertEquals("legacy-100", saved.value.deviceId)
    }
}
