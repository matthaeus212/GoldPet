package com.goldpet.domain.auth.service

import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.auth.entity.RefreshTokenRevokeReason
import com.goldpet.domain.auth.entity.UserRefreshToken
import com.goldpet.domain.auth.repository.UserRefreshTokenRepository
import com.goldpet.domain.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

/**
 * V66: JWT refresh token rotation + reuse detection.
 * 2026-07 보강: 하이브리드 앱(React WebView + Flutter 보안저장소)이 토큰을 각자
 * 독립 회전시키는 구조적 이중화 때문에, 한쪽이 회전시킨 직후 다른 쪽이 구세대 토큰으로
 * refresh 를 시도하는 "stale-generation" 상황이 상시 발생한다. 근본 수정(Flutter 역동기화)은
 * 앱 업데이트가 필요하므로, 서버는 "정상 회전으로 밀려났을 뿐인 구세대 토큰"을 관용하는
 * chain-walk 정책으로 완화한다 (기존 5초 grace window 는 이 정책에 포섭되어 제거됨).
 *
 * 알고리즘:
 * 1. `validateRefreshToken` 통과 → token_hash (SHA-256) 계산.
 * 2. `findByTokenHash`:
 *    - **row 없음** → legacy compat: pre-V66 발급 token 또는 lazy init. 새 token 발급 + DB insert (parent=NULL).
 *    - **row 있고 revoked=false** → 정상 회전. 새 token 발급 + DB insert (parent=oldHash) + old revoke(ROTATED).
 *    - **row 있고 revoked=true**:
 *      * **revokeReason == ROTATED** 인 경우에 한해 parent 체인을 따라 내려가며(최대
 *        [MAX_CHAIN_WALK_HOPS] hop) 살아있는 head 를 찾는다. `findByParentTokenHash` 는 **List** 를
 *        반환한다 — branch 발급이 반복되면 한 부모가 자식을 2개 이상 가질 수 있어(같은 parent 에서
 *        여러 번 stale-generation 회전) 단건(Optional) 가정이 깨진다. 각 hop 에서: (i) 동일
 *        device_id·revokedAt==null 인 자식이 있으면 그것이 살아있는 head → 즉시 관용(branch 발급)
 *        (ii) 없으면 동일 device_id·revokeReason==ROTATED 인 자식 중 issuedAt 최신 하나로 하강해
 *        walk 계속 (iii) 그 자식도 없으면(자식이 아예 없거나 전부 device 불일치/비-ROTATED 종단) 죽은
 *        체인으로 간주.
 *      * 그 외 전부 — `revokeReason` 이 ROTATED 가 아니거나(LOGOUT/ADMIN_FORCE/REUSE_DETECTED/EXPIRED),
 *        hop 상한 내에 살아있는 head 를 못 찾으면(죽은 체인) — `revokeAllForDevice(REUSE_DETECTED)` + 401.
 *
 * 보안 트레이드오프:
 * 1) 공격자가 직전 세대(혹은 그 이전) ROTATED 토큰을 탈취해 재생하면, 체인이 아직 살아있는 한
 *    branch 발급이 허용된다 — 즉 진짜 reuse 와 "다른 클라이언트가 늦게 회전"을 서버가 구분할 수
 *    없다는 것을 의도적으로 받아들인 절충이다.
 * 2) **증폭 효과**: 탈취된 구세대 ROTATED 토큰 하나만 있으면, 그 토큰을 반복 재생해 매번 별개의
 *    sibling 활성 토큰(branch)을 무제한으로 계속 발급받을 수 있다 — 즉 그 토큰이 살아있는 head 체인에
 *    연결돼 있는 한 영속적인 foothold 가 된다(체인 head 를 revoke 하지 않으므로 발급 횟수에 상한이
 *    없음). 이번 작업 범위에서는 발급 상한을 구현하지 않는다 — 문서화만 하고 별도 티켓으로 분리.
 * 알려진 한계: 하강은 각 레벨에서 ROTATED 자식 하나(issuedAt 최신, 동률 시 id)로만 내려가는 단일 경로
 * greedy 탐색이다. 트리가 분기한 뒤 "최신 자식 서브트리는 죽었지만 더 오래된 형제 서브트리에 살아있는
 * head 가 있는" 위상에서는 관용하지 못하고 REUSE_DETECTED 로 처리한다 — 드문 케이스로 수용하며,
 * 필요해지면 hop 예산 내 BFS 로 확장한다.
 *
 * 이중 클라이언트 구조가 해소되면(Flutter/React 토큰 저장소 단일화) 이 관용 정책은 제거하고 즉시
 * REUSE_DETECTED 로 되돌려야 한다. **Flutter 역동기화가 반영된 앱 버전이 사용자 기반에 충분히
 * 보급되면(예: 강제 업데이트 이후 구버전 트래픽이 무시할 수준으로 감소하면) 이 관용 정책 자체의
 * 회수(원복)를 검토할 것.**
 *
 * 트랜잭션: rotation 전체를 단일 `@Transactional`로 묶음.
 * `revokeByTokenHash` 후 새 token row insert 가 함께 commit → race condition 시 한 쪽만 성공.
 */
@Service
class RefreshTokenRotationService(
    private val repo: UserRefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Initial issue at login / signup. RotationService 가 호출되는 1순위 진입점.
     * 호출 측은 [issueInitial] 결과 token 을 클라이언트에 반환.
     */
    @Transactional
    fun issueInitial(
        userId: Long,
        deviceId: String,
        userAgent: String? = null,
        ipAddress: String? = null,
    ): String = persistNewToken(userId, deviceId, parentHash = null, userAgent, ipAddress)

    /**
     * Rotate. 반환은 (newRefreshToken, userId).
     * 검증 실패 / reuse 시 [UnauthorizedException].
     *
     * `noRollbackFor = UnauthorizedException`: reuse-detection 경로(d)는
     * [revokeAllForDevice] 로 device chain 전체를 revoke 한 직후 401 을 던진다.
     * 기본 rollback 정책이면 이 revoke 가 함께 롤백되어 보안 조치가 무효화되므로,
     * UnauthorizedException 발생 시에도 revoke 가 commit 되도록 명시한다.
     */
    @Transactional(noRollbackFor = [UnauthorizedException::class])
    fun rotate(
        oldToken: String,
        deviceId: String?,
        userAgent: String? = null,
        ipAddress: String? = null,
    ): RotationResult {
        if (!jwtTokenProvider.validateRefreshToken(oldToken)) {
            throw UnauthorizedException("Invalid refresh token")
        }
        val userId = jwtTokenProvider.getUserIdFromRefreshToken(oldToken)
        val oldHash = hashToken(oldToken)
        val now = LocalDateTime.now()
        val resolvedDeviceId = deviceId?.takeIf { it.isNotBlank() } ?: "legacy-$userId"

        val existing = repo.findByTokenHash(oldHash).orElse(null)

        // (a) Legacy compat: pre-V66 token 또는 lazy init.
        if (existing == null) {
            val newToken = persistNewToken(userId, resolvedDeviceId, parentHash = null, userAgent, ipAddress)
            return RotationResult(newToken, userId)
        }

        // (b) 정상 회전.
        if (existing.revokedAt == null) {
            repo.revokeByTokenHash(oldHash, now, RefreshTokenRevokeReason.ROTATED)
            val newToken = persistNewToken(existing.userId, existing.deviceId, parentHash = oldHash, userAgent, ipAddress)
            return RotationResult(newToken, existing.userId)
        }

        // (c) revoked: ROTATED 로 인한 회전이었다면 parent 체인을 따라 내려가 살아있는
        // head 를 찾는다 (dual-client desync 관용). 그 외에는 즉시 reuse detection 으로.
        // 주의: 한 부모가 자식을 2개 이상 가질 수 있으므로(branch 발급 반복) 매 hop 마다
        // findByParentTokenHash 의 List 전체를 조회해 살아있는 자식/ROTATED 자식을 가려낸다.
        if (existing.revokeReason == RefreshTokenRevokeReason.ROTATED) {
            var cur = existing
            var depth = 0
            while (depth < MAX_CHAIN_WALK_HOPS) {
                val children = repo.findByParentTokenHash(cur.tokenHash).filter { it.deviceId == existing.deviceId }
                if (children.isEmpty()) break // 죽은 체인(자식 없음) 또는 전부 기기 불일치

                val aliveHead = children.firstOrNull { it.revokedAt == null }
                if (aliveHead != null) {
                    // 살아있는 head 도달 — stale-generation 관용, head 는 revoke 하지 않고 branch 발급.
                    depth++
                    log.info(
                        "stale-generation refresh accepted (dual-client desync) user={} device={} depth={}",
                        existing.userId, existing.deviceId, depth,
                    )
                    val newToken = persistNewToken(existing.userId, existing.deviceId, parentHash = aliveHead.tokenHash, userAgent, ipAddress)
                    return RotationResult(newToken, existing.userId)
                }

                // 살아있는 자식은 없음 — ROTATED 로 더 회전된 자식 중 가장 최근(issuedAt) 발급분으로 하강.
                // issuedAt 동률 시 id 로 tie-break 해 하강 경로를 결정적으로 유지한다.
                val next = children
                    .filter { it.revokeReason == RefreshTokenRevokeReason.ROTATED }
                    .maxWithOrNull(compareBy({ it.issuedAt }, { it.id }))
                    ?: break // 내려갈 ROTATED 자식 없음 — 죽은 체인
                depth++
                cur = next
            }
            // hop 상한 초과 또는 죽은 체인(head 도 이미 revoke) — reuse detection 으로 fall through.
        }

        // (d) REUSE DETECTED — 해당 device chain 전체 revoke.
        val revoked = repo.revokeAllForDevice(existing.userId, existing.deviceId, now, RefreshTokenRevokeReason.REUSE_DETECTED)
        log.warn(
            "Refresh token reuse detected — user={} device={} revoked_count={}",
            existing.userId, existing.deviceId, revoked,
        )
        throw UnauthorizedException("Refresh token reuse detected — all tokens for this device revoked")
    }

    private fun persistNewToken(
        userId: Long,
        deviceId: String,
        parentHash: String?,
        userAgent: String?,
        ipAddress: String?,
    ): String {
        val newToken = jwtTokenProvider.generateRefreshToken(userId)
        val newHash = hashToken(newToken)
        val expiresAt = jwtTokenProvider.getRefreshExpiry(newToken)
        repo.save(
            UserRefreshToken(
                userId = userId,
                deviceId = deviceId,
                tokenHash = newHash,
                parentTokenHash = parentHash,
                expiresAt = expiresAt,
                userAgent = userAgent,
                ipAddress = ipAddress,
            )
        )
        return newToken
    }

    private fun hashToken(token: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    data class RotationResult(val newRefreshToken: String, val userId: Long)

    companion object {
        /** parent 체인 walk 무한루프 방지 상한 (dual-client desync 관용 정책). */
        const val MAX_CHAIN_WALK_HOPS: Int = 20
    }
}
