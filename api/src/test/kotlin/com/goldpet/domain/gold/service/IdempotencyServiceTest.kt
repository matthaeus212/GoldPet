package com.goldpet.domain.gold.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.exception.ConflictException
import com.goldpet.domain.gold.entity.GoldIdempotencyKey
import com.goldpet.domain.gold.entity.IdempotencyStatus
import com.goldpet.domain.gold.repository.GoldIdempotencyKeyRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

class IdempotencyServiceTest {

    @Mock
    private lateinit var repo: GoldIdempotencyKeyRepository

    private lateinit var objectMapper: ObjectMapper
    private lateinit var service: IdempotencyService

    private data class TestResponse(val txId: Long, val amount: Int)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        objectMapper = jacksonObjectMapper()
        service = IdempotencyService(repo, objectMapper)
    }

    @Test
    fun `execute should run fn and persist completed on new key`() {
        // Given: new key — tryInsert returns 1
        whenever(repo.tryInsert(eq("key-1"), eq(100L), any())).thenReturn(1)

        // When
        val response = TestResponse(txId = 999L, amount = 50)
        val result = service.execute(
            key = "key-1",
            userId = 100L,
            requestHash = "hash-A",
            responseType = TestResponse::class.java,
        ) { response to response.txId }

        // Then
        assertEquals(response, result)
        verify(repo).markCompleted(
            eq("key-1"),
            eq(IdempotencyStatus.COMPLETED),
            eq(999L),
            any(),  // response JSON
            eq(200.toShort()),
            any(),  // LocalDateTime.now()
        )
    }

    @Test
    fun `execute should return cached response on duplicate completed key`() {
        // Given: tryInsert returns 0 (conflict), existing record is COMPLETED
        whenever(repo.tryInsert(eq("key-2"), eq(100L), any())).thenReturn(0)
        val cached = TestResponse(txId = 777L, amount = 30)
        val cachedJson = objectMapper.writeValueAsString(cached)
        val existing = GoldIdempotencyKey(
            idempotencyKey = "key-2",
            userId = 100L,
            requestHash = "hash-B",
            transactionId = 777L,
            responseBody = cachedJson,
            status = IdempotencyStatus.COMPLETED,
            httpStatus = 200,
            createdAt = LocalDateTime.now().minusMinutes(1),
            completedAt = LocalDateTime.now().minusMinutes(1),
            expiresAt = LocalDateTime.now().plusDays(7),
        )
        whenever(repo.findById("key-2")).thenReturn(Optional.of(existing))

        // When
        val result = service.execute(
            key = "key-2",
            userId = 100L,
            requestHash = "hash-B",
            responseType = TestResponse::class.java,
        ) { error("fn must not be called when cached response exists") }

        // Then
        assertEquals(cached, result)
        verify(repo, never()).markCompleted(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `execute should throw IDEMPOTENCY_PROCESSING when previous attempt still in flight`() {
        // Given
        whenever(repo.tryInsert(eq("key-3"), eq(100L), any())).thenReturn(0)
        val existing = GoldIdempotencyKey(
            idempotencyKey = "key-3",
            userId = 100L,
            requestHash = "hash-C",
            status = IdempotencyStatus.PROCESSING,
            createdAt = LocalDateTime.now().minusSeconds(2),
            expiresAt = LocalDateTime.now().plusDays(7),
        )
        whenever(repo.findById("key-3")).thenReturn(Optional.of(existing))

        // When & Then
        val ex = assertThrows<ConflictException> {
            service.execute("key-3", 100L, "hash-C", TestResponse::class.java) {
                error("fn must not run on PROCESSING conflict")
            }
        }
        assertEquals("IDEMPOTENCY_PROCESSING", ex.errorCode)
    }

    @Test
    fun `execute should throw IDEMPOTENCY_KEY_MISUSE when request hash differs from existing`() {
        // Given
        whenever(repo.tryInsert(eq("key-4"), eq(100L), any())).thenReturn(0)
        val existing = GoldIdempotencyKey(
            idempotencyKey = "key-4",
            userId = 100L,
            requestHash = "ORIGINAL_HASH",
            status = IdempotencyStatus.COMPLETED,
            responseBody = "{}",
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusDays(7),
        )
        whenever(repo.findById("key-4")).thenReturn(Optional.of(existing))

        // When & Then — same key, different body hash
        val ex = assertThrows<BadRequestException> {
            service.execute("key-4", 100L, "DIFFERENT_HASH", TestResponse::class.java) {
                error("fn must not run on hash mismatch")
            }
        }
        assertEquals("IDEMPOTENCY_KEY_MISUSE", ex.errorCode)
    }

    @Test
    fun `execute should throw IDEMPOTENCY_PREVIOUSLY_FAILED on prior failed attempt`() {
        // Given
        whenever(repo.tryInsert(eq("key-5"), eq(100L), any())).thenReturn(0)
        val existing = GoldIdempotencyKey(
            idempotencyKey = "key-5",
            userId = 100L,
            requestHash = "hash-E",
            status = IdempotencyStatus.FAILED,
            createdAt = LocalDateTime.now().minusMinutes(5),
            completedAt = LocalDateTime.now().minusMinutes(5),
            expiresAt = LocalDateTime.now().plusDays(7),
        )
        whenever(repo.findById("key-5")).thenReturn(Optional.of(existing))

        // When & Then
        val ex = assertThrows<ConflictException> {
            service.execute("key-5", 100L, "hash-E", TestResponse::class.java) {
                error("fn must not run on FAILED conflict")
            }
        }
        assertEquals("IDEMPOTENCY_PREVIOUSLY_FAILED", ex.errorCode)
    }

    @Test
    fun `execute should reject blank or oversized key`() {
        assertThrows<IllegalArgumentException> {
            service.execute("", 100L, "h", TestResponse::class.java) { error("never") }
        }
        assertThrows<IllegalArgumentException> {
            service.execute("x".repeat(65), 100L, "h", TestResponse::class.java) { error("never") }
        }
        verify(repo, times(0)).tryInsert(any(), any(), any())
    }

    @Test
    fun `hashRequest should produce stable SHA-256 for same input`() {
        val body = mapOf("amount" to 100, "method" to "CARD")
        val a = service.hashRequest(100L, "POST /api/v1/gold/charge", body)
        val b = service.hashRequest(100L, "POST /api/v1/gold/charge", body)
        val different = service.hashRequest(100L, "POST /api/v1/gold/charge", mapOf("amount" to 200))

        assertEquals(a, b)
        assertNotEquals(a, different)
        assertEquals(64, a.length)  // SHA-256 hex
    }

    private inline fun <reified T> eq(value: T): T = org.mockito.kotlin.eq(value)
}
