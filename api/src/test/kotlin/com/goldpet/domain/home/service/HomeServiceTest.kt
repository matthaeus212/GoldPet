package com.goldpet.domain.home.service

import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.friend.service.FriendService
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.repository.GoldTransactionRepository
import com.goldpet.domain.notification.service.NotificationService
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.repository.WalkRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class HomeServiceTest {

    @Mock private lateinit var friendService: FriendService
    @Mock private lateinit var walkRepository: WalkRepository
    @Mock private lateinit var goldTransactionRepository: GoldTransactionRepository
    @Mock private lateinit var notificationService: NotificationService
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var petRepository: PetRepository
    @Mock private lateinit var fileAttachmentLookupService: FileAttachmentLookupService
    @Mock private lateinit var systemSettingService: SystemSettingService

    @InjectMocks
    private lateinit var homeService: HomeService

    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    @Test
    fun `getHomeData_shouldReturnHomeResponse_whenUserExists`() {
        val userId = 1L
        val user = User(
            id = userId, email = "home@test.com", oauthProvider = "KAKAO", oauthId = "k1",
            username = null, password = null, nickname = "HomeUser",
            name = null, birthDate = null, phoneNumber = null,
            gender = null, birthYear = null, mainLocationText = "Seoul",
            mainLocationGeom = null, profileImageUrl = null
        )

        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        // reco v2 OFF → 기존 getFriends 거리순 경로 유지
        whenever(systemSettingService.getBoolean(eq("reco.recommendation_v2.enabled"), any()))
            .thenReturn(false)
        whenever(friendService.getFriends(
            eq(userId), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), any()
        )).thenReturn(PageImpl(emptyList()))
        whenever(walkRepository.findFirstByUserIdOrderByStartTimeDesc(userId)).thenReturn(null)
        whenever(notificationService.getUnreadCount(userId)).thenReturn(0L)
        whenever(petRepository.findByOwnerId(userId)).thenReturn(emptyList())
        whenever(walkRepository.aggregateWalkStats(eq(userId), any(), any()))
            .thenReturn(listOf(arrayOf<Any>(0.0, 0L, 0.0, 0L)))
        whenever(goldTransactionRepository.sumAmountByUserAndTypeSince(eq(userId), eq(TransactionType.REWARD), any()))
            .thenReturn(null)

        val result = homeService.getHomeData(userId)

        assertThat(result).isNotNull
        assertThat(result.recommendations).isEmpty()
        assertThat(result.recentWalk).isNull()
        assertThat(result.notifications.count).isEqualTo(0L)
        assertThat(result.todayWalk.distanceKm).isEqualTo(0.0)
        assertThat(result.todayWalk.earnedGold).isEqualTo(0)
    }

    @Test
    fun `getHomeData_shouldCallGetRecommendationsWithOppositeGenderAndHasLocation_whenRecoV2Enabled`() {
        val userId = 2L
        val lat = 37.5665
        val lng = 126.9780
        val user = User(
            id = userId, email = "reco@test.com", oauthProvider = "KAKAO", oauthId = "k2",
            username = null, password = null, nickname = "RecoUser",
            name = null, birthDate = null, phoneNumber = null,
            gender = "MALE", birthYear = null, mainLocationText = "Seoul",
            mainLocationGeom = geometryFactory.createPoint(Coordinate(lng, lat)), profileImageUrl = null
        )

        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        // reco v2 ON → getRecommendations 경로(반경무제한·이성우선·거리순).
        whenever(systemSettingService.getBoolean(eq("reco.recommendation_v2.enabled"), any()))
            .thenReturn(true)
        whenever(friendService.getRecommendations(
            eq(userId), any(), any(), anyOrNull(), any(), any()
        )).thenReturn(emptyList())
        whenever(walkRepository.findFirstByUserIdOrderByStartTimeDesc(userId)).thenReturn(null)
        whenever(notificationService.getUnreadCount(userId)).thenReturn(0L)
        whenever(petRepository.findByOwnerId(userId)).thenReturn(emptyList())
        whenever(walkRepository.aggregateWalkStats(eq(userId), any(), any()))
            .thenReturn(listOf(arrayOf<Any>(0.0, 0L, 0.0, 0L)))
        whenever(goldTransactionRepository.sumAmountByUserAndTypeSince(eq(userId), eq(TransactionType.REWARD), any()))
            .thenReturn(null)

        val result = homeService.getHomeData(userId)

        assertThat(result).isNotNull
        assertThat(result.recommendations).isEmpty()
        // MALE 요청자 → oppositeGender=FEMALE, mainLocationGeom 설정됨 → hasMyLocation=true 로 위임 호출됨을 검증.
        verify(friendService).getRecommendations(
            myUserId = eq(userId),
            myLat = eq(lat),
            myLng = eq(lng),
            oppositeGender = eq("FEMALE"),
            hasMyLocation = eq(true),
            limit = eq(10)
        )
    }
}
