package com.goldpet.domain.walk

import com.goldpet.IntegrationTestBase
import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.notification.dto.CreateNotificationRequest
import com.goldpet.domain.notification.entity.NotificationType
import com.goldpet.domain.notification.service.FcmMulticastResult
import com.goldpet.domain.notification.service.FcmPushSender
import com.goldpet.domain.notification.service.FcmSendResult
import com.goldpet.domain.notification.service.NotificationService
import com.goldpet.domain.user.entity.DeviceType
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserDevice
import com.goldpet.domain.user.repository.UserDeviceRepository
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import java.util.UUID

/**
 * BLOCKER #13 (BE) — 다중 기기 push fan-out 통합 테스트.
 *
 * 검증 대상: 한 유저에게 활성 기기가 2개 등록되어 있을 때, 산책 완료 등으로 발생한
 * 사용자 알림이 [NotificationService.createNotification] 의 multi-device fan-out 경로를 통해
 * **모든 활성 기기의 FCM 토큰으로 1회 multicast 송신**되는지 (= 2기기 fan-out) 를 mock
 * [FcmPushSender] 로 단언한다.
 *
 * 산책 완료 시 발생하는 사용자 알림(NotificationType.WALK)은 다른 모든 푸시와 동일하게
 * 이 fan-out 경로를 공유하므로, 본 IT 가 walk-end 다중 기기 push 회귀를 가드한다.
 *
 * 실행 전: `./deploy-local/scripts/start.sh` 로 local PostGIS(5433) 기동.
 */
@Tag("integration")
class WalkMultiDevicePushIT : IntegrationTestBase() {

    @Autowired private lateinit var notificationService: NotificationService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var userDeviceRepository: UserDeviceRepository

    // LoggingFcmPushSender(@Profile("test")) 를 mock 으로 대체 — fan-out 호출 인자 검증용.
    @MockBean private lateinit var fcmPushSender: FcmPushSender

    private val uid: String = UUID.randomUUID().toString().replace("-", "").take(10)
    private lateinit var walker: User
    private val tokenA = "fcm-token-A-$uid"
    private val tokenB = "fcm-token-B-$uid"

    @BeforeEach
    fun setUp() {
        walker = userRepository.save(
            User(
                id = 0,
                email = "walker_$uid@goldpet.com",
                emailHash = BlindIndexUtil.hash("walker_$uid@goldpet.com"),
                oauthProvider = "LOCAL",
                oauthId = "walker_$uid",
                username = null,
                password = null,
                nickname = "Walker_$uid",
                name = null,
                birthDate = null,
                phoneNumber = null,
                gender = null,
                birthYear = null,
                mainLocationText = null,
                mainLocationGeom = null,
                profileImageUrl = null,
            )
        )
    }

    @AfterEach
    fun tearDown() {
        runCatching { userDeviceRepository.deleteAllByUserId(walker.id) }
        runCatching { userRepository.delete(walker) }
    }

    private fun registerDevice(deviceId: String, fcmToken: String) {
        userDeviceRepository.save(
            UserDevice(
                user = walker,
                deviceId = deviceId,
                fcmToken = fcmToken,
                deviceType = DeviceType.ANDROID,
                isActive = true,
            )
        )
    }

    @Test
    fun `2기기 활성 등록 시 walk 알림이 양 기기 토큰으로 multicast fan-out 된다`() {
        registerDevice("device-A-$uid", tokenA)
        registerDevice("device-B-$uid", tokenB)

        whenever(fcmPushSender.sendToMultipleDetailed(any(), any(), any(), any()))
            .thenReturn(FcmMulticastResult(successCount = 2, invalidTokens = emptyList(), totalSent = 2))

        notificationService.createNotification(
            CreateNotificationRequest(
                userId = walker.id,
                type = NotificationType.WALK,
                title = "산책 완료",
                message = "산책을 완료하고 골드를 획득했어요!",
            )
        )

        // multicast(fan-out) 1회 호출 + 두 기기 토큰 모두 포함 검증.
        val tokensCaptor = argumentCaptor<List<String>>()
        verify(fcmPushSender, times(1)).sendToMultipleDetailed(tokensCaptor.capture(), any(), any(), any())
        assertThat(tokensCaptor.firstValue)
            .withFailMessage("2기기 fan-out 은 두 기기의 FCM 토큰을 모두 포함해야 한다. 실제=%s", tokensCaptor.firstValue)
            .containsExactlyInAnyOrder(tokenA, tokenB)

        // 단일 송신 경로는 호출되지 않아야 한다 (multi-device 분기 확인).
        verify(fcmPushSender, never()).sendDetailed(any(), any(), any(), any())
    }

    @Test
    fun `단일 기기만 활성일 때는 단일 sendDetailed 경로로 송신된다 (대조)`() {
        registerDevice("device-only-$uid", tokenA)

        whenever(fcmPushSender.sendDetailed(any(), any(), any(), any()))
            .thenReturn(FcmSendResult.Success)

        notificationService.createNotification(
            CreateNotificationRequest(
                userId = walker.id,
                type = NotificationType.WALK,
                title = "산책 완료",
                message = "산책을 완료하고 골드를 획득했어요!",
            )
        )

        verify(fcmPushSender, times(1)).sendDetailed(any(), any(), any(), any())
        verify(fcmPushSender, never()).sendToMultipleDetailed(any(), any(), any(), any())
    }
}
