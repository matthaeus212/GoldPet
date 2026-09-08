package com.goldpet.domain.gold.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.exception.ConflictException
import com.goldpet.domain.gold.entity.IdempotencyStatus
import com.goldpet.domain.gold.repository.GoldIdempotencyKeyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

/**
 * 골드 트랜잭션 idempotency 보장.
 *
 * 사용 패턴:
 * ```
 * val hash = idempotencyService.hashRequest(userId, "POST /api/v1/gold/charge", request)
 * val response = idempotencyService.execute(key, userId, hash, TransactionResponse::class.java) {
 *     val tx = goldService.chargeGold(userId, request)
 *     tx to tx.id
 * }
 * ```
 *
 * 동작:
 * 1. `INSERT ... ON CONFLICT DO NOTHING` — 신규 키: PROCESSING 저장 후 fn 실행.
 * 2. 충돌: 기존 row SELECT 후 status에 따라 분기.
 *    - request_hash 불일치 → BadRequestException(IDEMPOTENCY_KEY_MISUSE)  // 422 적합하나 기존 핸들러 400 매핑
 *    - PROCESSING → ConflictException(IDEMPOTENCY_PROCESSING)
 *    - COMPLETED → 저장된 response_body deserialize 반환
 *    - FAILED → ConflictException(IDEMPOTENCY_PREVIOUSLY_FAILED) — 새 키로 retry
 * 3. fn 성공 시 markCompleted(COMPLETED).
 *
 * 트랜잭션 모델 (옵션 A 베타):
 * - idempotency record + fn 결과를 동일 트랜잭션. fn 예외 시 함께 rollback → 동일 key 재시도 가능.
 * - POST-LAUNCH (옵션 B / PG webhook): FAILED status를 영구 차단으로 보존하려면 REQUIRES_NEW 별도 트랜잭션 필요.
 */
@Service
class IdempotencyService(
    private val repo: GoldIdempotencyKeyRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    fun <T : Any> execute(
        key: String,
        userId: Long,
        requestHash: String,
        responseType: Class<T>,
        fn: () -> Pair<T, Long?>,
    ): T {
        require(key.isNotBlank()) { "Idempotency-Key must not be blank" }
        require(key.length <= 64) { "Idempotency-Key too long (max 64 chars)" }

        val inserted = repo.tryInsert(key, userId, requestHash)

        if (inserted == 0) {
            val existing = repo.findById(key).orElseThrow {
                ConflictException("Idempotency key vanished mid-conflict: $key")
            }
            if (existing.requestHash != requestHash) {
                throw BadRequestException(
                    "Idempotency-Key reuse with different request body",
                    errorCode = "IDEMPOTENCY_KEY_MISUSE",
                )
            }
            return when (existing.status) {
                IdempotencyStatus.PROCESSING -> throw ConflictException(
                    "Request still processing — retry after 1s",
                    errorCode = "IDEMPOTENCY_PROCESSING",
                )
                IdempotencyStatus.COMPLETED -> {
                    val body = existing.responseBody
                        ?: throw IllegalStateException("Idempotency $key COMPLETED but responseBody is null")
                    objectMapper.readValue(body, responseType)
                }
                IdempotencyStatus.FAILED -> throw ConflictException(
                    "Previous attempt failed — retry with a new Idempotency-Key",
                    errorCode = "IDEMPOTENCY_PREVIOUSLY_FAILED",
                )
            }
        }

        val (response, txId) = fn()
        val responseJson = objectMapper.writeValueAsString(response)
        repo.markCompleted(
            key = key,
            status = IdempotencyStatus.COMPLETED,
            transactionId = txId,
            responseBody = responseJson,
            httpStatus = HTTP_OK,
            now = LocalDateTime.now(),
        )
        return response
    }

    /**
     * SHA-256 hash of (userId + endpoint + serialized body).
     * 동일 key + 다른 hash → IDEMPOTENCY_KEY_MISUSE.
     */
    fun hashRequest(userId: Long, endpoint: String, body: Any): String {
        val bodyJson = objectMapper.writeValueAsString(body)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("$userId|$endpoint|$bodyJson".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val HTTP_OK: Short = 200
    }
}
