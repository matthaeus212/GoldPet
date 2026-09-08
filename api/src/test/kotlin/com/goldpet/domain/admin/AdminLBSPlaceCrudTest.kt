package com.goldpet.domain.admin

import com.goldpet.IntegrationTestBase
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.AdminUserPrincipal
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.checkin.entity.Place
import com.goldpet.domain.checkin.entity.PlaceCategory
import com.goldpet.domain.checkin.repository.PlaceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * P1.2 LBS place PUT/DELETE 기능 검증.
 *
 * - PUT 이름/카테고리 변경
 * - PUT 좌표 변경 + 좌표 round-trip 정밀도 검증
 * - DELETE soft-delete (deletedAt 설정, 목록에서 제거)
 * - 존재하지 않는 ID 에 대한 404 응답
 */
@AutoConfigureMockMvc
@Tag("integration")
class AdminLBSPlaceCrudTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtTokenProvider: JwtTokenProvider
    @Autowired private lateinit var adminUserRepository: AdminUserRepository
    @Autowired private lateinit var placeRepository: PlaceRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val ts = System.currentTimeMillis()
    private lateinit var superAdminUser: AdminUser
    private val createdPlaceIds = mutableListOf<Long>()
    private val gf = GeometryFactory(PrecisionModel(), 4326)

    @BeforeEach
    fun setUp() {
        superAdminUser = adminUserRepository.save(AdminUser(
            email = "lbs_super_$ts@goldpet.com",
            passwordHash = passwordEncoder.encode("pass"),
            name = "LBS SuperAdmin",
            role = AdminUserRole.SUPER_ADMIN,
            otpSecret = null,
            isActive = true
        ))
    }

    @AfterEach
    fun tearDown() {
        createdPlaceIds.forEach { id -> runCatching { placeRepository.deleteById(id) } }
        runCatching { adminUserRepository.delete(superAdminUser) }
    }

    private fun createPlace(name: String, lat: Double = 37.566, lng: Double = 126.977): Place {
        val place = placeRepository.save(Place(
            name = name,
            category = PlaceCategory.PARK,
            locationGeom = gf.createPoint(Coordinate(lng, lat))
        ))
        createdPlaceIds.add(place.id)
        return place
    }

    private fun jwtFor(adminUser: AdminUser): String {
        val principal = AdminUserPrincipal(adminUser)
        val auth = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        return jwtTokenProvider.generateToken(auth)
    }

    // ── GET ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `GET places returns list including the created place`() {
        val place = createPlace("테스트공원_$ts")
        mockMvc.perform(
            get("/api/v1/admin/lbs/places")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == ${place.id})].name").value(place.name))
    }

    // ── PUT name + category ───────────────────────────────────────────────────────────────

    @Test
    fun `PUT place updates name and category`() {
        val place = createPlace("원래이름_$ts")
        mockMvc.perform(
            put("/api/v1/admin/lbs/places/${place.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"새이름_$ts","category":"CAFE"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(place.id))
            .andExpect(jsonPath("$.name").value("새이름_$ts"))
            .andExpect(jsonPath("$.category").value("CAFE"))
    }

    // ── PUT coordinate round-trip ─────────────────────────────────────────────────────────

    @Test
    fun `PUT place updates coordinates and round-trips latitude and longitude`() {
        val place = createPlace("좌표테스트_$ts", lat = 37.500000, lng = 127.000000)
        val newLat = 37.123456
        val newLng = 126.654321
        mockMvc.perform(
            put("/api/v1/admin/lbs/places/${place.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"latitude":$newLat,"longitude":$newLng}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.latitude").value(newLat))
            .andExpect(jsonPath("$.longitude").value(newLng))
    }

    @Test
    fun `PUT place with only latitude preserves existing longitude`() {
        val origLng = 127.000000
        val place = createPlace("경도보존_$ts", lat = 37.500000, lng = origLng)
        mockMvc.perform(
            put("/api/v1/admin/lbs/places/${place.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"latitude":37.999}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.latitude").value(37.999))
            .andExpect(jsonPath("$.longitude").value(origLng))
    }

    @Test
    fun `PUT non-existent place returns 404`() {
        mockMvc.perform(
            put("/api/v1/admin/lbs/places/999999999")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"없음"}""")
        ).andExpect(status().isNotFound)
    }

    // ── DELETE soft-delete ────────────────────────────────────────────────────────────────

    @Test
    fun `DELETE place soft-deletes by setting deletedAt and removes from list`() {
        val place = createPlace("소프트삭제_$ts")
        mockMvc.perform(
            delete("/api/v1/admin/lbs/places/${place.id}")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
        ).andExpect(status().isNoContent)

        val inDb = placeRepository.findById(place.id).orElse(null)
        assertThat(inDb).isNotNull
        assertThat(inDb!!.deletedAt).isNotNull

        mockMvc.perform(
            get("/api/v1/admin/lbs/places")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == ${place.id})]").isEmpty)
    }

    @Test
    fun `DELETE non-existent place returns 404`() {
        mockMvc.perform(
            delete("/api/v1/admin/lbs/places/999999999")
                .header("Authorization", "Bearer ${jwtFor(superAdminUser)}")
        ).andExpect(status().isNotFound)
    }
}
