package com.goldpet.domain.checkin.integration

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.checkin.entity.Place
import com.goldpet.domain.checkin.entity.PlaceCategory
import com.goldpet.domain.checkin.repository.PlaceRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Tag
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.OptimisticLockingFailureException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * V63 마이그레이션에서 추가된 [Place.version] (@Version) 필드의 동시성 동작을 검증한다.
 *
 * 두 스레드가 같은 Place를 동시에 update 할 때 정확히 한 스레드만 성공하고
 * 나머지 한 스레드는 [OptimisticLockingFailureException]을 받아야 한다.
 *
 * 로컬 PostGIS 컨테이너(localhost:5433)를 사용하므로
 * `./deploy-local/scripts/start.sh` 실행 후 테스트를 수행해야 한다.
 */
@Tag("integration")
class AdminLBSPlaceConcurrencyIT : IntegrationTestBase() {

    @Autowired
    private lateinit var placeRepository: PlaceRepository

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)
    private var placeId: Long = -1

    @BeforeEach
    fun setUpPlace() {
        val saved = placeRepository.save(
            Place(
                name = "ConcurrencyTest Place",
                category = PlaceCategory.PARK,
                locationGeom = geometryFactory.createPoint(Coordinate(126.9780, 37.5665))
            )
        )
        placeId = saved.id
    }

    @AfterEach
    fun tearDownPlace() {
        runCatching { placeRepository.deleteById(placeId) }
    }

    /**
     * CountDownLatch(1)로 2 스레드를 동시 출발시켜 같은 Place에 대한 saveAndFlush 경쟁을 유도한다.
     *
     * 메인 스레드에서 version=0인 detached 스냅샷 2개를 미리 로드한 뒤 latch를 해제하여
     * DB-level WHERE version=? 충돌이 반드시 발생하도록 보장한다.
     *
     * 검증: success=1, OptimisticLockingFailureException=1 — 5회 반복 PASS.
     */
    @RepeatedTest(5)
    fun `concurrent Place update triggers OptimisticLockingFailureException on one thread`() {
        // version=0인 detached 스냅샷 2개를 메인 스레드에서 미리 로드
        val snap1 = placeRepository.findById(placeId).orElseThrow()
        val snap2 = placeRepository.findById(placeId).orElseThrow()

        val startLatch = CountDownLatch(1)
        val successCount = AtomicInteger(0)
        val conflictCount = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(2)

        val f1 = executor.submit {
            snap1.name = "Thread-1 update"
            startLatch.await()
            try {
                placeRepository.saveAndFlush(snap1)
                successCount.incrementAndGet()
            } catch (e: OptimisticLockingFailureException) {
                conflictCount.incrementAndGet()
            }
        }

        val f2 = executor.submit {
            snap2.name = "Thread-2 update"
            startLatch.await()
            try {
                placeRepository.saveAndFlush(snap2)
                successCount.incrementAndGet()
            } catch (e: OptimisticLockingFailureException) {
                conflictCount.incrementAndGet()
            }
        }

        startLatch.countDown() // 두 스레드 동시 출발
        f1.get(10, TimeUnit.SECONDS)
        f2.get(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(1, successCount.get(), "정확히 한 스레드만 성공해야 함")
        assertEquals(1, conflictCount.get(), "정확히 한 스레드만 OptimisticLockingFailureException을 받아야 함")
    }
}
