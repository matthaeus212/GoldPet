package com.goldpet.domain.friend.repository

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

/**
 * `FriendRepository.findRecommendations`(홈 추천 v2) 회귀 테스트.
 *
 * 검증 항목(플랜 트랙1 검증):
 *   (a) 반경 밖(원거리) 유저 포함 — ST_DWithin 반경 필터 제거 확인
 *   (b) 펫 없는 유저 포함 — pets INNER JOIN 제거 확인(테스트 유저는 전부 펫 미등록)
 *   (c) 위치 NULL 유저가 맨 뒤 — dist NULLS LAST
 *   (d) 이성이 동성보다 앞 — gender-boolean 선행 정렬키
 *   (e) is_location_sharing_enabled=false 유저는 제외 — 프라이버시 옵트아웃 존중
 *
 * impl: `FriendRepository.kt` findRecommendations
 */
@Tag("integration")
class FriendRecommendationRepositoryTest : IntegrationTestBase() {

    @Autowired private lateinit var friendRepository: FriendRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val suffix = UUID.randomUUID().toString().take(8)
    private val geom = GeometryFactory(PrecisionModel(), 4326)

    // 요청자(정렬 기준 좌표 = 서울시청)
    private lateinit var me: User
    private val saved = mutableListOf<User>()

    // 서울시청 / 부산(원거리 ~325km)
    private val seoulLat = 37.5665
    private val seoulLng = 126.978
    private val busanLat = 35.1796
    private val busanLng = 129.0756

    @org.junit.jupiter.api.BeforeEach
    fun setUpRequester() {
        me = persist(makeUser("reco_me_$suffix", gender = "MALE", lat = seoulLat, lng = seoulLng))
    }

    @AfterEach
    fun tearDown() {
        saved.forEach { u -> runCatching { userRepository.delete(u) } }
        saved.clear()
    }

    // (a) 반경 밖 유저 포함 + (b) 펫 없는 유저 포함
    @Test
    fun `includes far-away and pet-less users (no radius filter, no pet join)`() {
        val far = persist(makeUser("reco_far_$suffix", gender = null, lat = busanLat, lng = busanLng))

        val result = friendRepository.findRecommendations(
            myUserId = me.id,
            myLat = seoulLat,
            myLng = seoulLng,
            oppositeGender = "FEMALE",
            hasMyLocation = true,
            pageable = PageRequest.of(0, 10_000)
        )

        // 원거리 + 펫 미등록임에도 결과에 포함
        assertThat(result.map { it.id }).contains(far.id)
    }

    // (c) 위치 NULL 유저가 맨 뒤
    @Test
    fun `null-location user sorts after users with location`() {
        // gender 무관하게: 요청자 좌표 有 → 거리순, 위치 NULL 은 NULLS LAST
        val nearNoGender = persist(makeUser("reco_near_$suffix", gender = null, lat = seoulLat, lng = seoulLng))
        val nullLoc = persist(makeUser("reco_nullloc_$suffix", gender = null, lat = null, lng = null))

        val result = friendRepository.findRecommendations(
            myUserId = me.id,
            myLat = seoulLat,
            myLng = seoulLng,
            oppositeGender = null, // 요청자 gender NULL 취급 → 이성 CASE 무효 → 순수 거리순
            hasMyLocation = true,
            pageable = PageRequest.of(0, 10_000)
        )

        val ids = result.map { it.id }
        assertThat(ids).contains(nearNoGender.id, nullLoc.id)
        assertThat(ids.indexOf(nearNoGender.id))
            .`as`("위치 있는 유저가 위치 NULL 유저보다 앞")
            .isLessThan(ids.indexOf(nullLoc.id))
    }

    // (d) 이성이 동성보다 앞 (거리와 무관하게 gender-boolean 이 선행)
    @Test
    fun `opposite gender sorts before same gender regardless of distance`() {
        // 이성(FEMALE)은 원거리, 동성(MALE)은 근거리 — 그래도 이성이 먼저여야 gender 선행 확인
        val oppositeFar = persist(makeUser("reco_opp_$suffix", gender = "FEMALE", lat = busanLat, lng = busanLng))
        val sameNear = persist(makeUser("reco_same_$suffix", gender = "MALE", lat = seoulLat, lng = seoulLng))

        val result = friendRepository.findRecommendations(
            myUserId = me.id,
            myLat = seoulLat,
            myLng = seoulLng,
            oppositeGender = "FEMALE",
            hasMyLocation = true,
            pageable = PageRequest.of(0, 10_000)
        )

        val ids = result.map { it.id }
        assertThat(ids).contains(oppositeFar.id, sameNear.id)
        assertThat(ids.indexOf(oppositeFar.id))
            .`as`("이성(원거리)이 동성(근거리)보다 먼저")
            .isLessThan(ids.indexOf(sameNear.id))
    }

    // (e) is_location_sharing_enabled=false 유저 제외
    @Test
    fun `excludes users who opted out of location sharing`() {
        val optedOut = persist(
            makeUser("reco_optout_$suffix", gender = "FEMALE", lat = seoulLat, lng = seoulLng)
                .apply { isLocationSharingEnabled = false }
        )

        val result = friendRepository.findRecommendations(
            myUserId = me.id,
            myLat = seoulLat,
            myLng = seoulLng,
            oppositeGender = "FEMALE",
            hasMyLocation = true,
            pageable = PageRequest.of(0, 10_000)
        )

        assertThat(result.map { it.id }).doesNotContain(optedOut.id)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun persist(user: User): User = userRepository.save(user).also { saved += it }

    private fun makeUser(oauthId: String, gender: String?, lat: Double?, lng: Double?) = User(
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
        gender = gender,
        birthYear = null,
        mainLocationText = null,
        mainLocationGeom = if (lat != null && lng != null) geom.createPoint(Coordinate(lng, lat)) else null,
        profileImageUrl = null,
    )
}
