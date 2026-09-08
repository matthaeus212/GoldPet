package com.goldpet.domain.gold.service

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.common.exception.DomainException
import com.goldpet.domain.gold.entity.IdempotencyStatus
import com.goldpet.domain.gold.repository.GoldIdempotencyKeyRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * V65 idempotency 동시성 검증 — Sprint 1 Acceptance "100 thread × 50회, 중복 트랜잭션 0건".
 *
 * 실행 전: `./deploy-local/scripts/start.sh` 로 local PostGIS(5433) 기동 필요.
 * 인프라가 없으면 [IntegrationTestBase.verifyLocalDatasource] 가 즉시 fail.
 */
class IdempotencyConcurrencyIT : IntegrationTestBase() {

    @Autowired private lateinit var idempotencyService: IdempotencyService
    @Autowired private lateinit var idempotencyRepo: GoldIdempotencyKeyRepository
    @Autowired private lateinit var userRepository: UserRepository

    private lateinit var testUser: User

    @BeforeEach
    fun seedIdempotencyTable() {
        idempotencyRepo.deleteAll()
        testUser = userRepository.findAll().firstOrNull()
            ?: error("Test DB must seed at least one user — run a seed migration or DB-init script first")
    }

    private data class FakeResponse(val txId: Long, val message: String)

    @Test
    fun `100 concurrent threads with same idempotency-key produce exactly one transaction`() {
        val key = UUID.randomUUID().toString()
        val hash = "shared-hash"
        val concurrency = 100
        val pool = Executors.newFixedThreadPool(concurrency)
        val gate = CountDownLatch(1)
        val fnExecutionCount = AtomicInteger(0)
        val successes = ConcurrentLinkedQueue<FakeResponse>()
        val errorCodes = ConcurrentLinkedQueue<String>()

        try {
            val futures = (1..concurrency).map {
                pool.submit {
                    try {
                        gate.await()
                        val resp = idempotencyService.execute(
                            key = key,
                            userId = testUser.id,
                            requestHash = hash,
                            responseType = FakeResponse::class.java,
                        ) {
                            val n = fnExecutionCount.incrementAndGet()
                            Thread.sleep(80)  // hold PROCESSING window long enough for racers to observe it
                            FakeResponse(txId = n.toLong(), message = "winner-$n") to n.toLong()
                        }
                        successes.offer(resp)
                    } catch (e: Throwable) {
                        val code = (e as? DomainException)?.errorCode ?: e.javaClass.simpleName
                        errorCodes.offer(code)
                    }
                }
            }
            gate.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }

        // fn MUST run exactly once
        assertEquals(
            1, fnExecutionCount.get(),
            "Idempotency fn must execute exactly once across $concurrency concurrent calls — actual=${fnExecutionCount.get()}, errors=$errorCodes",
        )

        // exactly one idempotency row exists
        assertEquals(1, idempotencyRepo.count())

        val row = idempotencyRepo.findById(key).orElseThrow()
        assertEquals(IdempotencyStatus.COMPLETED, row.status)
        assertNotNull(row.responseBody)

        // every successful response (cached) is the winner's response
        if (successes.isNotEmpty()) {
            val first = successes.first()
            successes.forEach { assertEquals(first, it, "All cached responses must match the winner's") }
        }

        // total accounted = concurrency
        assertEquals(concurrency, successes.size + errorCodes.size)

        // error responses must be IDEMPOTENCY_PROCESSING (winner was sleeping) — no surprises
        errorCodes.forEach { code ->
            check(code == "IDEMPOTENCY_PROCESSING" || code == "IDEMPOTENCY_KEY_MISUSE") {
                "Unexpected error code under concurrent contention: $code"
            }
        }
    }

    @Test
    fun `50 unique idempotency-keys with 2 concurrent calls each — fn runs exactly 50 times`() {
        val uniqueKeyCount = 50
        val callsPerKey = 2
        val total = uniqueKeyCount * callsPerKey
        val keys = (1..uniqueKeyCount).map { UUID.randomUUID().toString() }
        val pool = Executors.newFixedThreadPool(total)
        val gate = CountDownLatch(1)
        val fnExecutionCount = AtomicInteger(0)
        val errorCodes = ConcurrentLinkedQueue<String>()

        try {
            val futures = keys.flatMap { key ->
                (1..callsPerKey).map {
                    pool.submit {
                        try {
                            gate.await()
                            idempotencyService.execute(
                                key = key,
                                userId = testUser.id,
                                requestHash = "hash-$key",
                                responseType = FakeResponse::class.java,
                            ) {
                                val n = fnExecutionCount.incrementAndGet()
                                FakeResponse(txId = n.toLong(), message = "tx-$n") to n.toLong()
                            }
                        } catch (e: Throwable) {
                            val code = (e as? DomainException)?.errorCode ?: e.javaClass.simpleName
                            errorCodes.offer(code)
                        }
                    }
                }
            }
            gate.countDown()
            futures.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }

        // fn must run exactly once per unique key
        // (PROCESSING races are possible within a key, treated as errors — but cumulative fn count must be ≤ key count)
        check(fnExecutionCount.get() <= uniqueKeyCount) {
            "fn execution count ${fnExecutionCount.get()} exceeds unique key count $uniqueKeyCount — duplicate transaction"
        }

        // every key has exactly one row, COMPLETED
        assertEquals(uniqueKeyCount.toLong(), idempotencyRepo.count())
        idempotencyRepo.findAll().forEach { row ->
            assertEquals(IdempotencyStatus.COMPLETED, row.status, "Row ${row.idempotencyKey} status=${row.status}")
        }

        // no unexpected error codes
        errorCodes.forEach { code ->
            check(code == "IDEMPOTENCY_PROCESSING") {
                "Unexpected error code under multi-key concurrent contention: $code"
            }
        }
    }
}
