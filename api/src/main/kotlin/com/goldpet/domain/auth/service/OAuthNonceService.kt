package com.goldpet.domain.auth.service

import com.goldpet.domain.auth.entity.OAuthNonce
import com.goldpet.domain.auth.repository.OAuthNonceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * V67: OAuth flow nonce 서비스 — replay 차단.
 *
 * 사용 패턴:
 * ```
 * // OAuth flow 시작 시
 * val nonce = oauthNonceService.issue(userId, "LINK_SUGGESTION", ttlSeconds = 300)
 * val tempToken = jwtBuilder.claim("nonce", nonce.toString()).compact()
 *
 * // callback 시
 * val nonceClaim = claims["nonce"] as? String
 *     ?: throw BadRequestException("missing nonce")
 * if (!oauthNonceService.consume(nonceClaim)) {
 *     throw BadRequestException("nonce replay rejected")
 * }
 * ```
 */
@Service
class OAuthNonceService(
    private val repo: OAuthNonceRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Issue a new nonce + persist DB row. Returns the UUID for caller to embed in JWT/state. */
    @Transactional
    fun issue(userId: Long?, provider: String, ttlSeconds: Long = DEFAULT_TTL_SECONDS): UUID {
        require(provider.isNotBlank()) { "provider must not be blank" }
        val nonce = UUID.randomUUID()
        repo.save(
            OAuthNonce(
                nonceUuid = nonce,
                userId = userId,
                provider = provider,
                expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds),
            )
        )
        return nonce
    }

    /**
     * Atomic consume.
     * @return true if successfully consumed (first use). false if nonce missing, malformed, already used, or expired.
     */
    @Transactional
    fun consume(nonceStr: String): Boolean {
        val uuid = try {
            UUID.fromString(nonceStr)
        } catch (e: IllegalArgumentException) {
            log.warn("Invalid nonce format: '{}'", nonceStr)
            return false
        }
        val consumed = repo.tryConsume(uuid) == 1
        if (!consumed) {
            log.warn("Nonce consume failed (replay/expired/missing): {}", uuid)
        }
        return consumed
    }

    companion object {
        const val DEFAULT_TTL_SECONDS: Long = 300  // 5분
    }
}
