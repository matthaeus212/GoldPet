package com.goldpet.domain.walk.controller

import com.amazonaws.services.s3.AmazonS3
import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.entity.Walk
import com.goldpet.domain.walk.entity.WalkSpot
import com.goldpet.domain.walk.entity.WalkSpotType
import com.goldpet.domain.walk.repository.WalkRepository
import com.goldpet.domain.walk.repository.WalkSpotRepository
import com.goldpet.domain.walk.service.PhotoUrlSigner
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.URL
import java.time.LocalDateTime
import java.util.UUID

/**
 * Integration tests for walk-photo presigned URL contract.
 *
 * Scope: HTTP contract, Spring Security wiring, and end-to-end auth paths.
 * NOT duplicating unit tests already in WalkServiceTest / PhotoUrlSignerTest /
 * WalkSpotAdminDtoTest (flag logic, UUID safety, DTO field mapping).
 *
 * AmazonS3 is mocked so tests run without real MinIO.
 * SystemSettingService is spied so individual tests can flip the feature flag.
 */
@AutoConfigureMockMvc
@Tag("integration")
class WalkPhotoIntegrationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var walkRepository: WalkRepository
    @Autowired private lateinit var walkSpotRepository: WalkSpotRepository
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    /** Prevents FileService from attempting real MinIO connections. */
    @MockBean private lateinit var amazonS3: AmazonS3

    /**
     * Full mock (not spy) so flag stubbing is reliable across the Spring context.
     * setUp configures safe defaults; enablePresignFlag() overrides the flag key per-test.
     */
    @MockBean private lateinit var systemSettingService: SystemSettingService

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    // 각 테스트 인스턴스마다 새로운 UUID를 생성한다. 고정 UUID를 재사용하면
    // 격리 결함 발생 시 동일 키가 실 DB에 반복 누적되고 MinIO에 존재하지 않는 키를
    // 가리키는 고아 row가 실사용자 피드에 노출되는 문제가 있었다.
    private val PHOTO_KEY  = "${UUID.randomUUID()}.jpg"
    private val HIDDEN_KEY = "${UUID.randomUUID()}.jpg"
    private val FAKE_SIGNED_URL =
        "http://localhost:9100/goldpet-private/$PHOTO_KEY?X-Amz-Signature=testsig&X-Amz-Expires=1800"

    private lateinit var testUser: User
    private lateinit var otherUser: User
    private lateinit var testWalk: Walk
    private var photoSpotId: Long = -1
    private var hiddenSpotId: Long = -1
    private lateinit var adminUser: AdminUser
    private val ts = System.currentTimeMillis()

    @BeforeEach
    fun setUp() {
        // S3: doesBucketExistV2 → true (skips createBucket in FileService.init)
        //     generatePresignedUrl → fake signed URL with X-Amz-Signature
        whenever(amazonS3.doesBucketExistV2(any())).thenReturn(true)
        whenever(amazonS3.generatePresignedUrl(any())).thenReturn(URL(FAKE_SIGNED_URL))

        // SystemSettingService defaults: return the second arg (the provided default value).
        // This mirrors production behaviour when no override row exists in system_settings.
        whenever(systemSettingService.getString(any(), any()))
            .thenAnswer { it.getArgument<String>(1) }
        whenever(systemSettingService.getInt(any(), any()))
            .thenAnswer { it.getArgument<Int>(1) }

        testUser  = userRepository.save(makeUser("wp_owner_$ts"))
        otherUser = userRepository.save(makeUser("wp_other_$ts"))

        val path = geometryFactory.createLineString(arrayOf(
            Coordinate(126.9780, 37.5665),
            Coordinate(126.9785, 37.5670)
        ))
        testWalk = walkRepository.save(Walk(
            user = testUser,
            startTime = LocalDateTime.now().minusHours(1),
            endTime = LocalDateTime.now(),
            distanceKm = 1.5,
            durationSeconds = 3600,
            path = path,
            caloriesBurned = null,
            notes = null,
            isPublic = true
        ))

        val loc = geometryFactory.createPoint(Coordinate(126.9783, 37.5668))

        photoSpotId = walkSpotRepository.save(WalkSpot(
            walk = testWalk,
            location = loc,
            type = WalkSpotType.PHOTO,
            timestamp = LocalDateTime.now().minusMinutes(30),
            imageUrl = PHOTO_KEY,
            hiddenFromPublic = false
        )).id

        hiddenSpotId = walkSpotRepository.save(WalkSpot(
            walk = testWalk,
            location = loc,
            type = WalkSpotType.PHOTO,
            timestamp = LocalDateTime.now().minusMinutes(20),
            imageUrl = HIDDEN_KEY,
            hiddenFromPublic = true
        )).id

        adminUser = adminUserRepository.save(AdminUser(
            email = "wp_admin_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("adminPass123"),
            name = "포토테스트관리자",
            role = AdminUserRole.SUPER_ADMIN,
            otpSecret = null,
            isActive = true
        ))
    }

    @AfterEach
    fun tearDown() {
        // Reset mocks so flag stubs don't bleed between tests
        Mockito.reset(amazonS3, systemSettingService)
        // Walk cascade or explicit spot deletion — both safe via runCatching
        runCatching { walkSpotRepository.deleteById(photoSpotId) }
        runCatching { walkSpotRepository.deleteById(hiddenSpotId) }
        runCatching { walkRepository.delete(testWalk) }
        runCatching { userRepository.delete(testUser) }
        runCatching { userRepository.delete(otherUser) }
        runCatching { adminUserRepository.delete(adminUser) }
    }

    // ── GET /api/v1/walks/my/photos ───────────────────────────────────────────

    @Test
    fun `getMyPhotos returns 401 without authentication`() {
        mockMvc.perform(get("/api/v1/walks/my/photos"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getMyPhotos flag on returns presigned imageUrl starting with http and raw imageKey`() {
        enablePresignFlag()

        mockMvc.perform(
            get("/api/v1/walks/my/photos")
                .header("Authorization", "Bearer ${userToken(testUser)}")
        )
            .andExpect(status().isOk)
            // filter returns a list → use hasItem to check membership
            .andExpect(jsonPath("$.content[?(@.id == $photoSpotId)].imageUrl", hasItem(FAKE_SIGNED_URL)))
            .andExpect(jsonPath("$.content[?(@.id == $photoSpotId)].imageKey", hasItem(PHOTO_KEY)))
    }

    @Test
    fun `getMyPhotos flag off imageUrl is null after T1-4 (no rawKey fallback)`() {
        // flag stays at default "false" → signing returns null → T1-4 에서 rawKey fallback 제거되어 null
        mockMvc.perform(
            get("/api/v1/walks/my/photos")
                .header("Authorization", "Bearer ${userToken(testUser)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[?(@.id == $photoSpotId)].imageUrl", hasItem(nullValue())))
    }

    // ── GET /api/v1/walks/public/photos ───────────────────────────────────────

    @Test
    fun `getPublicPhotos hidden photo suppressed from public feed`() {
        val result = mockMvc.perform(
            get("/api/v1/walks/public/photos")
                .header("Authorization", "Bearer ${userToken(testUser)}")
        )
            .andExpect(status().isOk)
            .andReturn()

        val body = result.response.contentAsString
        // hiddenSpotId must not appear; photoSpotId may appear
        assert(!body.contains("\"id\":$hiddenSpotId")) {
            "Hidden photo spot $hiddenSpotId must not be present in public photo feed"
        }
    }

    // ── GET /api/v1/walks/photos/{spotId}/url (mint endpoint) ─────────────────

    @Test
    fun `mintPhotoUrl returns 401 without authentication`() {
        mockMvc.perform(get("/api/v1/walks/photos/$photoSpotId/url"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `mintPhotoUrl owner gets 200 with correct JSON shape`() {
        enablePresignFlag()

        mockMvc.perform(
            get("/api/v1/walks/photos/$photoSpotId/url")
                .header("Authorization", "Bearer ${userToken(testUser)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.spotId").value(photoSpotId))
            .andExpect(jsonPath("$.imageUrl").value(FAKE_SIGNED_URL))
            .andExpect(jsonPath("$.imageKey").value(PHOTO_KEY))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty)
    }

    @Test
    fun `mintPhotoUrl flag off owner still gets 200 but imageUrl is null after T1-4`() {
        // flag off: signedUrlOrNull returns null.
        // T1-4: rawKey fallback 제거로 imageUrl=null. imageKey 는 여전히 유지.
        mockMvc.perform(
            get("/api/v1/walks/photos/$photoSpotId/url")
                .header("Authorization", "Bearer ${userToken(testUser)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imageUrl").value(nullValue()))
            .andExpect(jsonPath("$.imageKey").value(PHOTO_KEY))
    }

    @Test
    fun `mintPhotoUrl non-owner on hidden spot returns 403`() {
        mockMvc.perform(
            get("/api/v1/walks/photos/$hiddenSpotId/url")
                .header("Authorization", "Bearer ${userToken(otherUser)}")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `mintPhotoUrl nonexistent spot returns 404`() {
        mockMvc.perform(
            get("/api/v1/walks/photos/999999987/url")
                .header("Authorization", "Bearer ${userToken(testUser)}")
        )
            .andExpect(status().isNotFound)
    }

    // ── GET /api/v1/admin/walks/{walkId} — admin DTO parity ──────────────────

    @Test
    fun `admin walk detail includes signed imageUrl and raw imageKey for hidden photo spot`() {
        enablePresignFlag()

        mockMvc.perform(
            get("/api/v1/admin/walks/${testWalk.id}")
                .header("Authorization", "Bearer ${adminToken()}")
        )
            .andExpect(status().isOk)
            // admin sees ALL spots including hidden ones; filter returns list → hasItem
            .andExpect(jsonPath("$.spots[?(@.id == $hiddenSpotId)].imageUrl", hasItem(FAKE_SIGNED_URL)))
            .andExpect(jsonPath("$.spots[?(@.id == $hiddenSpotId)].imageKey", hasItem(HIDDEN_KEY)))
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Overrides the mock to return "true" for the presign feature flag. */
    private fun enablePresignFlag() {
        whenever(systemSettingService.getString(eq(PhotoUrlSigner.FLAG_KEY), any()))
            .thenReturn("true")
    }

    private fun userToken(user: User): String {
        val principal = UserPrincipal.create(user)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }

    private fun adminToken(): String {
        val principal = AdminUserPrincipal(adminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }

    private fun makeUser(oauthId: String) = User(
        id = 0,
        email = "$oauthId@goldpet.com",
        oauthProvider = "LOCAL",
        oauthId = oauthId,
        username = oauthId,
        password = passwordEncoder.encode("pass"),
        nickname = oauthId,
        name = oauthId,
        birthDate = null,
        phoneNumber = null,
        gender = null,
        birthYear = null,
        mainLocationText = null,
        mainLocationGeom = null,
        profileImageUrl = null
    )
}
