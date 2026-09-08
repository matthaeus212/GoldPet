package com.goldpet.domain.friend.service

import com.goldpet.domain.friend.repository.FriendRepository
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyDouble
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.util.Optional

/**
 * 친구찾기 '전체'(무제한) 분기 단위테스트.
 * distanceKm==null + reco v2 플래그 ON → findRecommendations(무제한·이성우선) 경유,
 * 특정 거리 또는 플래그 OFF → 기존 5km 경로 유지(회귀 방지).
 */
@ExtendWith(MockitoExtension::class)
class FriendServiceTest {

    @Mock
    private lateinit var friendRepository: FriendRepository

    @Mock
    private lateinit var petRepository: PetRepository

    @Mock
    private lateinit var fileAttachmentLookupService: com.goldpet.domain.common.service.FileAttachmentLookupService

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var systemSettingService: com.goldpet.domain.common.service.SystemSettingService

    @Mock
    private lateinit var profileBoostService: com.goldpet.domain.profileboost.service.ProfileBoostService

    @Mock
    private lateinit var experimentService: com.goldpet.domain.experiment.service.ExperimentService

    @InjectMocks
    private lateinit var friendService: FriendService

    private fun user(id: Long, gender: String?, withLocation: Boolean): User {
        val geom = if (withLocation) {
            GeometryFactory().createPoint(Coordinate(127.0, 37.0))
        } else null
        return User(
            id = id,
            email = "user$id@example.com",
            oauthProvider = "GOOGLE",
            oauthId = "google$id",
            username = null,
            password = null,
            nickname = "User$id",
            name = null,
            birthDate = null,
            phoneNumber = null,
            gender = gender,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = geom,
            profileImageUrl = null
        )
    }

    @Test
    fun `distance null with reco v2 flag ON routes through findRecommendations with opposite gender`() {
        val pageable = PageRequest.of(0, 10)
        val me = user(1L, "MALE", withLocation = true)
        val candidate = user(2L, "FEMALE", withLocation = false)

        whenever(systemSettingService.getBoolean("reco.recommendation_v2.enabled", false)).thenReturn(true)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(me))
        whenever(
            friendRepository.findRecommendations(1L, 37.0, 127.0, "FEMALE", true, pageable)
        ).thenReturn(listOf(candidate))

        val result = friendService.getFriends(1L, 37.0, 127.0, null, "registered", null, null, pageable)

        verify(friendRepository).findRecommendations(1L, 37.0, 127.0, "FEMALE", true, pageable)
        verify(friendRepository, never()).findFriendsNearbyByRegistered(anyLong(), anyDouble(), anyDouble(), anyDouble(), any())
        verify(friendRepository, never()).findFriendsNearbyByDistance(anyLong(), anyDouble(), anyDouble(), anyDouble(), any())
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].id).isEqualTo(2L)
    }

    @Test
    fun `unlimited path with full page of pageSize results has last false`() {
        val pageable = PageRequest.of(0, 10)
        val me = user(1L, "MALE", withLocation = true)
        // pageSize(10)개를 정확히 채워 반환 → 다음 페이지가 있다고 판단해야 함(last=false).
        val fullPage = (2L..11L).map { user(it, "FEMALE", withLocation = false) }

        whenever(systemSettingService.getBoolean("reco.recommendation_v2.enabled", false)).thenReturn(true)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(me))
        whenever(
            friendRepository.findRecommendations(1L, 37.0, 127.0, "FEMALE", true, pageable)
        ).thenReturn(fullPage)

        val result = friendService.getFriends(1L, 37.0, 127.0, null, "registered", null, null, pageable)

        assertThat(result.content).hasSize(10)
        assertThat(result.isLast).isFalse()
    }

    @Test
    fun `unlimited path with partial page under pageSize has last true`() {
        val pageable = PageRequest.of(0, 10)
        val me = user(1L, "MALE", withLocation = true)
        // pageSize(10) 미만(3개) 반환 → 더 이상 다음 페이지가 없다고 판단해야 함(last=true).
        val partialPage = (2L..4L).map { user(it, "FEMALE", withLocation = false) }

        whenever(systemSettingService.getBoolean("reco.recommendation_v2.enabled", false)).thenReturn(true)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(me))
        whenever(
            friendRepository.findRecommendations(1L, 37.0, 127.0, "FEMALE", true, pageable)
        ).thenReturn(partialPage)

        val result = friendService.getFriends(1L, 37.0, 127.0, null, "registered", null, null, pageable)

        assertThat(result.content).hasSize(3)
        assertThat(result.isLast).isTrue()
    }

    @Test
    fun `unlimited path with empty page has last true`() {
        val pageable = PageRequest.of(0, 10)
        val me = user(1L, "MALE", withLocation = true)

        whenever(systemSettingService.getBoolean("reco.recommendation_v2.enabled", false)).thenReturn(true)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(me))
        whenever(
            friendRepository.findRecommendations(1L, 37.0, 127.0, "FEMALE", true, pageable)
        ).thenReturn(emptyList())

        val result = friendService.getFriends(1L, 37.0, 127.0, null, "registered", null, null, pageable)

        assertThat(result.content).isEmpty()
        assertThat(result.isLast).isTrue()
    }

    @Test
    fun `distance null with reco v2 ON and sort popular honors sort with unlimited radius, bypasses reco`() {
        val pageable = PageRequest.of(0, 10)
        val unsortedPageable = PageRequest.of(0, 10)

        whenever(systemSettingService.getBoolean("reco.recommendation_v2.enabled", false)).thenReturn(true)
        whenever(
            friendRepository.findFriendsNearbyByPopular(1L, 37.0, 127.0, FriendService.UNLIMITED_RADIUS_METERS, unsortedPageable)
        ).thenReturn(PageImpl(emptyList<User>(), pageable, 0))

        friendService.getFriends(1L, 37.0, 127.0, null, "popular", null, null, pageable)

        verify(friendRepository).findFriendsNearbyByPopular(1L, 37.0, 127.0, FriendService.UNLIMITED_RADIUS_METERS, unsortedPageable)
        verify(friendRepository, never()).findRecommendations(anyLong(), anyDouble(), anyDouble(), any(), anyBoolean(), any())
    }

    @Test
    fun `distance null with reco v2 ON and sort compatible honors sort with unlimited radius, bypasses reco`() {
        val pageable = PageRequest.of(0, 10)
        val candidatePageable = PageRequest.of(0, FriendService.COMPATIBILITY_CANDIDATE_LIMIT)

        whenever(systemSettingService.getBoolean("reco.recommendation_v2.enabled", false)).thenReturn(true)
        whenever(systemSettingService.getBoolean("match.compatibility.enabled", false)).thenReturn(true)
        whenever(systemSettingService.getBoolean("experiment.compatibility.enabled", false)).thenReturn(false)
        // getCompatibleFriends 는 후보가 비어도 loadCompatibilityWeights()/boost bonus 조회를 먼저 수행한다 —
        // getString() 은 non-null String 반환 계약이라 unstubbed mock의 null 반환 시 toDoubleOrNull()에서 NPE.
        whenever(systemSettingService.getString("match.score.w_distance", "0.40")).thenReturn("0.40")
        whenever(systemSettingService.getString("match.score.w_interest", "0.25")).thenReturn("0.25")
        whenever(systemSettingService.getString("match.score.w_hobby", "0.20")).thenReturn("0.20")
        whenever(systemSettingService.getString("match.score.w_temperament", "0.15")).thenReturn("0.15")
        whenever(systemSettingService.getString("profile.boost.rank_bonus", "0.15")).thenReturn("0.15")
        whenever(
            friendRepository.findFriendsNearbyByDistance(1L, 37.0, 127.0, FriendService.UNLIMITED_RADIUS_METERS, candidatePageable)
        ).thenReturn(PageImpl(emptyList<User>(), candidatePageable, 0))

        friendService.getFriends(1L, 37.0, 127.0, null, "compatible", null, null, pageable)

        verify(friendRepository).findFriendsNearbyByDistance(1L, 37.0, 127.0, FriendService.UNLIMITED_RADIUS_METERS, candidatePageable)
        verify(friendRepository, never()).findRecommendations(anyLong(), anyDouble(), anyDouble(), any(), anyBoolean(), any())
    }

    // 회귀: 컨트롤러가 ?sort=registered 로 만든 Sort 실린 pageable 을 native @Query 에 그대로 넘기면
    // Hibernate 가 `ORDER BY u.registered`(존재 않는 컬럼)를 덧붙여 SQL 42601(친구찾기 500)이 났다.
    // findRecommendations 에는 반드시 Sort 를 벗긴 PageRequest.of(0,10) 이 넘어가야 한다.
    @Test
    fun `unlimited reco path strips Sort from pageable before native query`() {
        val sortedPageable = PageRequest.of(0, 10, Sort.by("registered"))
        val me = user(1L, "MALE", withLocation = true)
        whenever(systemSettingService.getBoolean("reco.recommendation_v2.enabled", false)).thenReturn(true)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(me))
        whenever(
            friendRepository.findRecommendations(1L, 37.0, 127.0, "FEMALE", true, PageRequest.of(0, 10))
        ).thenReturn(emptyList())

        friendService.getFriends(1L, 37.0, 127.0, null, "registered", null, null, sortedPageable)

        verify(friendRepository).findRecommendations(1L, 37.0, 127.0, "FEMALE", true, PageRequest.of(0, 10))
    }

    @Test
    fun `specific distance 5km stays on legacy nearby path`() {
        val pageable = PageRequest.of(0, 10)
        whenever(
            friendRepository.findFriendsNearbyByRegistered(1L, 37.0, 127.0, 5000.0, PageRequest.of(0, 10))
        ).thenReturn(PageImpl(emptyList<User>(), pageable, 0))

        friendService.getFriends(1L, 37.0, 127.0, 5.0, "registered", null, null, pageable)

        verify(friendRepository).findFriendsNearbyByRegistered(1L, 37.0, 127.0, 5000.0, PageRequest.of(0, 10))
        verify(friendRepository, never()).findRecommendations(anyLong(), anyDouble(), anyDouble(), any(), anyBoolean(), any())
    }

    @Test
    fun `distance null with reco v2 flag OFF falls back to 5km legacy path`() {
        val pageable = PageRequest.of(0, 10)
        whenever(systemSettingService.getBoolean("reco.recommendation_v2.enabled", false)).thenReturn(false)
        whenever(
            friendRepository.findFriendsNearbyByRegistered(1L, 37.0, 127.0, 5000.0, PageRequest.of(0, 10))
        ).thenReturn(PageImpl(emptyList<User>(), pageable, 0))

        friendService.getFriends(1L, 37.0, 127.0, null, "registered", null, null, pageable)

        verify(friendRepository).findFriendsNearbyByRegistered(1L, 37.0, 127.0, 5000.0, PageRequest.of(0, 10))
        verify(friendRepository, never()).findRecommendations(anyLong(), anyDouble(), anyDouble(), any(), anyBoolean(), any())
    }
}
