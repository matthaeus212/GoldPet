package com.goldpet.domain.notification.service

import com.goldpet.domain.notification.dto.CreateNotificationRequest
import com.goldpet.domain.notification.entity.Notification
import com.goldpet.domain.notification.entity.NotificationType
import com.goldpet.domain.notification.repository.NotificationRepository
import com.goldpet.domain.user.entity.DeviceType
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserDevice
import com.goldpet.domain.user.repository.UserDeviceRepository
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.common.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.*

class NotificationServiceTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userDeviceRepository: UserDeviceRepository

    @Mock
    private lateinit var fcmPushSender: com.goldpet.domain.notification.service.FcmPushSender

    private lateinit var notificationService: NotificationService

    private lateinit var testUser: User
    private lateinit var testFromUser: User

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        notificationService = NotificationService(notificationRepository, userRepository, userDeviceRepository, fcmPushSender)

        testUser = createTestUser(1L, "testuser", "테스트유저")
        testFromUser = createTestUser(2L, "fromuser", "보낸유저")
    }

    private fun createTestUser(id: Long, oauthId: String, nickname: String): User {
        return User(
            id = id,
            email = "$oauthId@example.com",
            oauthProvider = "LOCAL",
            oauthId = oauthId,
            username = oauthId,
            password = "password",
            nickname = nickname,
            name = nickname,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )
    }

    @Test
    fun `getNotifications should return paginated notifications`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val notification = Notification(
            id = 1L,
            user = testUser,
            type = NotificationType.LIKE,
            title = "새로운 좋아요",
            message = "홍길동님이 좋아요를 보냈습니다."
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        val notifications = listOf(notification)
        val page = PageImpl(notifications, pageable, 1)

        whenever(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(page)

        // When
        val result = notificationService.getNotifications(1L, null, pageable)

        // Then
        assertEquals(1, result.content.size)
        assertEquals("새로운 좋아요", result.content[0].title)
    }

    @Test
    fun `getNotifications should return all when category is ALL`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val notification = Notification(
            id = 1L,
            user = testUser,
            type = NotificationType.MATCH,
            title = "매칭 완료",
            message = "새로운 친구와 매칭되었습니다."
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        val notifications = listOf(notification)
        val page = PageImpl(notifications, pageable, 1)

        whenever(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(page)

        // When
        val result = notificationService.getNotifications(1L, "ALL", pageable)

        // Then
        assertEquals(1, result.content.size)
        assertEquals(NotificationType.MATCH, result.content[0].type)
    }

    @Test
    fun `getNotifications should filter by FRIEND category`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val notification = Notification(
            id = 1L,
            user = testUser,
            type = NotificationType.MATCH,
            title = "매칭 완료",
            message = "새로운 친구와 매칭되었습니다."
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        val notifications = listOf(notification)
        val page = PageImpl(notifications, pageable, 1)
        val friendTypes = listOf(NotificationType.LIKE, NotificationType.MATCH)

        whenever(notificationRepository.findAllByUserIdAndTypeInOrderByCreatedAtDesc(1L, friendTypes, pageable))
            .thenReturn(page)

        // When
        val result = notificationService.getNotifications(1L, "FRIEND", pageable)

        // Then
        assertEquals(1, result.content.size)
    }

    @Test
    fun `getNotifications should filter by CHAT category`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val notification = Notification(
            id = 1L,
            user = testUser,
            type = NotificationType.MESSAGE,
            title = "새 메시지",
            message = "안녕하세요!"
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
        val notifications = listOf(notification)
        val page = PageImpl(notifications, pageable, 1)
        val chatTypes = listOf(NotificationType.MESSAGE)

        whenever(notificationRepository.findAllByUserIdAndTypeInOrderByCreatedAtDesc(1L, chatTypes, pageable))
            .thenReturn(page)

        // When
        val result = notificationService.getNotifications(1L, "CHAT", pageable)

        // Then
        assertEquals(1, result.content.size)
        assertEquals(NotificationType.MESSAGE, result.content[0].type)
    }

    @Test
    fun `getUnreadCount should return count of unread notifications`() {
        // Given
        whenever(notificationRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(5L)

        // When
        val result = notificationService.getUnreadCount(1L)

        // Then
        assertEquals(5L, result)
    }

    @Test
    fun `markAsRead should mark notification as read`() {
        // Given
        val notification = Notification(
            id = 1L,
            user = testUser,
            type = NotificationType.LIKE,
            title = "테스트",
            message = "테스트 알림",
            isRead = false
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))
        whenever(notificationRepository.save(any<Notification>())).thenReturn(notification)

        // When
        notificationService.markAsRead(1L, 1L)

        // Then
        assertTrue(notification.isRead)
    }

    @Test
    fun `markAsRead should throw exception when notification not found`() {
        // Given
        whenever(notificationRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            notificationService.markAsRead(999L, 1L)
        }
    }

    @Test
    fun `markAsRead should throw exception when user is not owner`() {
        // Given
        val notification = Notification(
            id = 1L,
            user = testUser,
            type = NotificationType.LIKE,
            title = "테스트",
            message = "테스트 알림",
            isRead = false
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))

        // When & Then - user 999 is not the owner (testUser has id 1)
        assertThrows<ForbiddenException> {
            notificationService.markAsRead(1L, 999L)
        }
    }

    @Test
    fun `markAllAsRead should mark all notifications as read`() {
        // Given
        whenever(notificationRepository.markAllAsRead(1L)).thenReturn(10)

        // When
        val result = notificationService.markAllAsRead(1L)

        // Then
        assertEquals(10, result)
    }

    @Test
    fun `deleteNotification should delete notification`() {
        // Given
        val notification = Notification(
            id = 1L,
            user = testUser,
            type = NotificationType.LIKE,
            title = "테스트",
            message = "테스트 알림"
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))

        // When & Then
        assertDoesNotThrow {
            notificationService.deleteNotification(1L, 1L)
        }
    }

    @Test
    fun `deleteNotification should throw exception when notification not found`() {
        // Given
        whenever(notificationRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            notificationService.deleteNotification(999L, 1L)
        }
    }

    @Test
    fun `deleteNotification should throw exception when user is not owner`() {
        // Given
        val notification = Notification(
            id = 1L,
            user = testUser,
            type = NotificationType.LIKE,
            title = "테스트",
            message = "테스트 알림"
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))

        // When & Then
        assertThrows<ForbiddenException> {
            notificationService.deleteNotification(1L, 999L)
        }
    }

    @Test
    fun `createNotification should create new notification`() {
        // Given
        val request = CreateNotificationRequest(
            userId = 1L,
            type = NotificationType.LIKE,
            title = "새 알림",
            message = "알림 내용",
            targetId = 123L,
            targetType = "POST"
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { invocation ->
            val notification = invocation.getArgument<Notification>(0)
            Notification(
                id = 1L,
                user = notification.user,
                type = notification.type,
                title = notification.title,
                message = notification.message,
                targetId = notification.targetId,
                targetType = notification.targetType,
                senderId = notification.senderId,
                senderNickname = notification.senderNickname,
                senderProfileImage = notification.senderProfileImage
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = notificationService.createNotification(request)

        // Then
        assertNotNull(result)
        assertEquals("새 알림", result.title)
        assertEquals("알림 내용", result.message)
        assertEquals(NotificationType.LIKE, result.type)
    }

    @Test
    fun `createNotification should throw exception when user not found`() {
        // Given
        val request = CreateNotificationRequest(
            userId = 999L,
            type = NotificationType.LIKE,
            title = "새 알림",
            message = "알림 내용"
        )

        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            notificationService.createNotification(request)
        }
    }

    @Test
    fun `createNotification should include sender info when senderId provided`() {
        // Given
        val request = CreateNotificationRequest(
            userId = 1L,
            type = NotificationType.LIKE,
            title = "새 알림",
            message = "알림 내용",
            senderId = 2L
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(testFromUser))
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { invocation ->
            val notification = invocation.getArgument<Notification>(0)
            Notification(
                id = 1L,
                user = notification.user,
                type = notification.type,
                title = notification.title,
                message = notification.message,
                senderId = notification.senderId,
                senderNickname = notification.senderNickname,
                senderProfileImage = notification.senderProfileImage
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = notificationService.createNotification(request)

        // Then
        assertNotNull(result)
        assertEquals(2L, result.senderId)
        assertEquals("보낸유저", result.senderNickname)
    }

    @Test
    fun `notifyMatch should create match notification`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(testFromUser))
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { invocation ->
            val notification = invocation.getArgument<Notification>(0)
            Notification(
                id = 1L,
                user = notification.user,
                type = notification.type,
                title = notification.title,
                message = notification.message,
                targetId = notification.targetId,
                targetType = notification.targetType,
                senderId = notification.senderId,
                senderNickname = notification.senderNickname
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When & Then
        assertDoesNotThrow {
            notificationService.notifyMatch(1L, 2L, 100L)
        }
    }

    @Test
    fun `notifyLike should create like notification`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(testFromUser))
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { invocation ->
            val notification = invocation.getArgument<Notification>(0)
            Notification(
                id = 1L,
                user = notification.user,
                type = notification.type,
                title = notification.title,
                message = notification.message,
                targetId = notification.targetId,
                targetType = notification.targetType,
                senderId = notification.senderId
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When & Then
        assertDoesNotThrow {
            notificationService.notifyLike(1L, 2L, 100L)
        }
    }

    @Test
    fun `notifyComment should create comment notification`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(testFromUser))
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { invocation ->
            val notification = invocation.getArgument<Notification>(0)
            Notification(
                id = 1L,
                user = notification.user,
                type = notification.type,
                title = notification.title,
                message = notification.message,
                targetId = notification.targetId,
                targetType = notification.targetType,
                senderId = notification.senderId
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When & Then
        assertDoesNotThrow {
            notificationService.notifyComment(1L, 2L, 100L, "댓글 미리보기...")
        }
    }

    @Test
    fun `notifyMessage should create message notification`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(testFromUser))
        whenever(notificationRepository.save(any<Notification>())).thenAnswer { invocation ->
            val notification = invocation.getArgument<Notification>(0)
            Notification(
                id = 1L,
                user = notification.user,
                type = notification.type,
                title = notification.title,
                message = notification.message,
                targetId = notification.targetId,
                targetType = notification.targetType,
                senderId = notification.senderId
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When & Then
        assertDoesNotThrow {
            notificationService.notifyMessage(1L, 2L, 100L, "메시지 미리보기...")
        }
    }

    @Test
    fun `notifyMatch should not throw when fromUser not found`() {
        // Given - fromUser not found, should silently return
        whenever(userRepository.findById(2L)).thenReturn(Optional.empty())

        // When & Then - should not throw, just return early
        assertDoesNotThrow {
            notificationService.notifyMatch(1L, 2L, 100L)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // V70 BLOCKER #8 — FCM UNREGISTERED(InvalidToken) → deactivateByFcmToken cleanup
    // ──────────────────────────────────────────────────────────────────────────

    private fun activeDevice(deviceId: String, fcmToken: String) =
        UserDevice(
            user = testUser,
            deviceId = deviceId,
            fcmToken = fcmToken,
            deviceType = DeviceType.ANDROID,
            isActive = true,
        )

    @Test
    fun `createNotification deactivates a single device when FCM returns InvalidToken`() {
        // Given — 활성 기기 1대, 그 토큰이 UNREGISTERED 로 무효.
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(notificationRepository.save(any<Notification>())).thenAnswer {
            (it.getArgument(0) as Notification).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }
        whenever(userDeviceRepository.findAllByUserIdAndIsActiveTrue(1L))
            .thenReturn(listOf(activeDevice("device-1", "bad-token")))
        whenever(fcmPushSender.sendDetailed(any(), any(), any(), any()))
            .thenReturn(FcmSendResult.InvalidToken)

        // When
        notificationService.createNotification(
            CreateNotificationRequest(
                userId = 1L,
                type = NotificationType.WALK,
                title = "산책 완료",
                message = "보상 지급",
            )
        )

        // Then — 무효 토큰 1건 deactivate.
        verify(userDeviceRepository, times(1)).deactivateByFcmToken("bad-token")
    }

    @Test
    fun `createNotification deactivates only invalid tokens from multicast result`() {
        // Given — 활성 기기 2대(good/bad), multicast 응답이 bad 만 invalid 로 보고.
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(notificationRepository.save(any<Notification>())).thenAnswer {
            (it.getArgument(0) as Notification).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }
        whenever(userDeviceRepository.findAllByUserIdAndIsActiveTrue(1L))
            .thenReturn(listOf(activeDevice("device-1", "good-token"), activeDevice("device-2", "bad-token")))
        whenever(fcmPushSender.sendToMultipleDetailed(any(), any(), any(), any()))
            .thenReturn(FcmMulticastResult(successCount = 1, invalidTokens = listOf("bad-token"), totalSent = 2))

        // When
        notificationService.createNotification(
            CreateNotificationRequest(
                userId = 1L,
                type = NotificationType.WALK,
                title = "산책 완료",
                message = "보상 지급",
            )
        )

        // Then — invalid 토큰만 deactivate, 정상 토큰은 보존.
        verify(userDeviceRepository, times(1)).deactivateByFcmToken("bad-token")
        verify(userDeviceRepository, never()).deactivateByFcmToken("good-token")
    }
}
