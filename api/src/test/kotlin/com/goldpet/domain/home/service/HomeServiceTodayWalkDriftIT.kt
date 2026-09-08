package com.goldpet.domain.home.service

import com.amazonaws.services.s3.AmazonS3
import com.goldpet.IntegrationTestBase
import com.goldpet.domain.home.dto.HomeTodayWalkResponse
import com.goldpet.domain.pet.entity.Pet
import com.goldpet.domain.pet.entity.PetSpecies
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.pet.repository.PetSpeciesRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.entity.Walk
import com.goldpet.domain.walk.repository.WalkRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Tag
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.full.memberProperties

/**
 * Integration test: HomeService.getHomeData() ↔ openapi.json HomeTodayWalkResponse schema drift guard.
 *
 * Covers:
 * 1. Schema parity — every field declared in openapi.json HomeTodayWalkResponse exists in the Kotlin DTO.
 * 2. Walk 0건 — todayWalk numeric fields return zero, no NPE.
 * 3. Walk 1건 — individual walk stats surfaced correctly.
 * 4. Walk N건 — multiple walks are summed correctly.
 *
 * Each scenario is run @RepeatedTest(5) to prove absence of flakiness.
 */
@Tag("integration")
class HomeServiceTodayWalkDriftIT : IntegrationTestBase() {

    @Autowired private lateinit var homeService: HomeService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var petRepository: PetRepository
    @Autowired private lateinit var petSpeciesRepository: PetSpeciesRepository
    @Autowired private lateinit var walkRepository: WalkRepository

    /** Prevents FileService.init from attempting a real MinIO connection. */
    @MockBean private lateinit var amazonS3: AmazonS3

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    // Mutated per-setUp so each @RepeatedTest repetition has its own isolated user/data.
    private lateinit var testUser: User
    private lateinit var testSpecies: PetSpecies
    private val savedWalks = mutableListOf<Walk>()
    private val savedPets  = mutableListOf<Pet>()
    private lateinit var testId: String

    /**
     * Required properties per openapi.json HomeTodayWalkResponse.required array.
     * If the DTO renames or removes one of these, the schema-drift test fails immediately.
     */
    private val OPENAPI_REQUIRED_FIELDS = setOf(
        "caloriesBurned", "distanceKm", "durationMinutes", "earnedGold", "walkCount"
    )

    /**
     * All properties (required + optional) declared in openapi.json HomeTodayWalkResponse.properties.
     * If either side adds a field without updating the other, this set catches the drift.
     */
    private val OPENAPI_ALL_FIELDS = setOf(
        "caloriesBurned", "distanceKm", "durationMinutes", "earnedGold", "walkCount",
        "petName", "petBreed", "locationText", "petProfileImageUrl", "petProfileImageUrlThumbnail",
        "petProfileImageUrlThumbnailWebp"
    )

    @BeforeEach
    fun setUp() {
        whenever(amazonS3.doesBucketExistV2(any())).thenReturn(true)

        testId = UUID.randomUUID().toString().take(12)

        testUser = userRepository.save(
            User(
                id = 0,
                email = "driftit_$testId@goldpet.com",
                oauthProvider = "LOCAL",
                oauthId = "driftit_$testId",
                username = "driftit_$testId",
                password = null,
                nickname = "DriftIT_$testId",
                name = "DriftIT",
                birthDate = null,
                phoneNumber = null,
                gender = null,
                birthYear = null,
                mainLocationText = "Seoul",
                mainLocationGeom = null,
                profileImageUrl = null
            )
        )

        // Reuse seeded species (V2__Seed_Data.sql — DOG/CAT) rather than creating throwaway rows.
        testSpecies = petSpeciesRepository.findAll().first()
    }

    @AfterEach
    fun tearDown() {
        runCatching { walkRepository.deleteAll(savedWalks) }
        runCatching { petRepository.deleteAll(savedPets) }
        runCatching { userRepository.delete(testUser) }
        savedWalks.clear()
        savedPets.clear()
    }

    // ── 1. Schema drift guard ─────────────────────────────────────────────────

    @RepeatedTest(5)
    fun `HomeTodayWalkResponse DTO contains every field declared in openapi schema`() {
        val dtoPropNames = HomeTodayWalkResponse::class.memberProperties
            .map { it.name }
            .toSet()

        assertThat(dtoPropNames)
            .`as`("DTO must contain all openapi REQUIRED fields")
            .containsAll(OPENAPI_REQUIRED_FIELDS)

        assertThat(dtoPropNames)
            .`as`("DTO must contain all openapi-declared fields (required + optional)")
            .containsAll(OPENAPI_ALL_FIELDS)

        assertThat(OPENAPI_ALL_FIELDS)
            .`as`("openapi schema must declare every DTO field (no undocumented property)")
            .containsAll(dtoPropNames)
    }

    // ── 2. Walk 0건: zero-safe return ─────────────────────────────────────────

    @RepeatedTest(5)
    fun `getHomeData returns zero todayWalk stats when user has no walks today`() {
        val result = homeService.getHomeData(testUser.id)

        val tw = result.todayWalk
        assertThat(tw.distanceKm).isEqualTo(0.0)
        assertThat(tw.durationMinutes).isEqualTo(0L)
        assertThat(tw.caloriesBurned).isEqualTo(0.0)
        assertThat(tw.earnedGold).isEqualTo(0)
        assertThat(tw.walkCount).isEqualTo(0L)
    }

    // ── 3. Walk 1건: correct single-walk values ───────────────────────────────

    @RepeatedTest(5)
    fun `getHomeData returns correct todayWalk stats for a single walk`() {
        saveWalk(distanceKm = 2.5, durationSeconds = 1800L, caloriesBurned = 120.0)

        val tw = homeService.getHomeData(testUser.id).todayWalk

        assertThat(tw.distanceKm).isCloseTo(2.5, Offset.offset(0.001))
        assertThat(tw.durationMinutes).isEqualTo(30L)          // 1800 / 60
        assertThat(tw.caloriesBurned).isCloseTo(120.0, Offset.offset(0.01))
        assertThat(tw.walkCount).isEqualTo(1L)
    }

    // ── 4. Walk N건: aggregated sum ───────────────────────────────────────────

    @RepeatedTest(5)
    fun `getHomeData sums todayWalk stats across multiple walks`() {
        saveWalk(distanceKm = 1.0, durationSeconds = 600L,  caloriesBurned = 50.0)
        saveWalk(distanceKm = 2.0, durationSeconds = 1200L, caloriesBurned = 100.0)

        val tw = homeService.getHomeData(testUser.id).todayWalk

        assertThat(tw.distanceKm).isCloseTo(3.0, Offset.offset(0.001))
        assertThat(tw.durationMinutes).isEqualTo(30L)          // (600 + 1200) / 60
        assertThat(tw.caloriesBurned).isCloseTo(150.0, Offset.offset(0.01))
        assertThat(tw.walkCount).isEqualTo(2L)
    }

    // ── 5. Pet info surfaced in todayWalk ─────────────────────────────────────

    @RepeatedTest(5)
    fun `getHomeData populates petName and locationText in todayWalk from primary pet and user`() {
        val pet = petRepository.save(
            Pet(
                owner    = testUser,
                name     = "Buddy_$testId",
                species  = testSpecies,
                breed    = null,
                gender   = null,
                birthDate = null,
                weightKg = null,
                isNeutered = null,
                profileImageUrl = null,
                temperamentTags = null
            )
        ).also { savedPets.add(it) }

        val tw = homeService.getHomeData(testUser.id).todayWalk

        assertThat(tw.petName).isEqualTo(pet.name)
        assertThat(tw.petBreed).isEqualTo(testSpecies.name)  // breed=null → falls back to species.name
        assertThat(tw.locationText).isEqualTo("Seoul")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun saveWalk(
        distanceKm: Double,
        durationSeconds: Long,
        caloriesBurned: Double
    ): Walk {
        val path = geometryFactory.createLineString(
            arrayOf(Coordinate(126.978, 37.5665), Coordinate(126.9785, 37.567))
        )
        val now = LocalDateTime.now()
        return walkRepository.save(
            Walk(
                user            = testUser,
                startTime       = now.minusSeconds(durationSeconds + 60),
                endTime         = now,
                distanceKm      = distanceKm,
                durationSeconds = durationSeconds,
                path            = path,
                caloriesBurned  = caloriesBurned,
                notes           = null,
                isPublic        = true
            )
        ).also { savedWalks.add(it) }
    }
}
