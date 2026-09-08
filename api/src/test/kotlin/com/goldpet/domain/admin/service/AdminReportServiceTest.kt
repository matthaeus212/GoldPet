package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.ResolveReportRequest
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.chat.repository.ChatMessageRepository
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.community.repository.CommunityCommentRepository
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.course.repository.WalkCourseRepository
import com.goldpet.domain.notification.service.NotificationService
import com.goldpet.domain.report.entity.Report
import com.goldpet.domain.report.entity.ReportActionType
import com.goldpet.domain.report.entity.ReportStatus
import com.goldpet.domain.report.entity.ReportType
import com.goldpet.domain.report.repository.ReportRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.walk.repository.WalkSpotRepository
import com.goldpet.domain.walk.service.PhotoUrlSigner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.*

class AdminReportServiceTest {

    @Mock private lateinit var reportRepository: ReportRepository
    @Mock private lateinit var adminCommunityService: AdminCommunityService
    @Mock private lateinit var adminUserManagementService: AdminUserManagementService
    @Mock private lateinit var adminChatService: AdminChatService
    @Mock private lateinit var adminUserRepository: AdminUserRepository
    @Mock private lateinit var communityPostRepository: CommunityPostRepository
    @Mock private lateinit var communityCommentRepository: CommunityCommentRepository
    @Mock private lateinit var chatMessageRepository: ChatMessageRepository
    @Mock private lateinit var walkCourseRepository: WalkCourseRepository
    @Mock private lateinit var notificationService: NotificationService
    @Mock private lateinit var walkSpotRepository: WalkSpotRepository
    @Mock private lateinit var photoUrlSigner: PhotoUrlSigner

    private lateinit var service: AdminReportService
    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = AdminReportService(
            reportRepository, adminCommunityService, adminUserManagementService,
            adminChatService, adminUserRepository, communityPostRepository,
            communityCommentRepository, chatMessageRepository, walkCourseRepository, notificationService,
            walkSpotRepository, photoUrlSigner
        )

        testUser = User(
            id = 1L,
            email = "test@example.com",
            oauthProvider = "LOCAL",
            oauthId = "testuser",
            username = "testuser",
            password = "password",
            nickname = "신고자",
            name = "테스트",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )
    }

    private fun pendingPostReport(reporter: User, targetId: Long = 100L): Report {
        return Report(
            id = 1L,
            type = ReportType.POST,
            targetId = targetId,
            targetPreview = "게시글 내용",
            reason = "스팸",
            reporter = reporter,
            status = ReportStatus.PENDING
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
    }

    private fun pendingUserReport(reporter: User, targetId: Long = 2L): Report {
        return Report(
            id = 2L,
            type = ReportType.USER,
            targetId = targetId,
            targetPreview = "피신고자",
            reason = "부적절한 행동",
            reporter = reporter,
            status = ReportStatus.PENDING
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
    }

    private fun resolvedReport(): Report {
        return Report(
            id = 3L,
            type = ReportType.USER,
            targetId = 2L,
            targetPreview = "피신고자",
            reason = "부적절한 행동",
            reporter = testUser,
            status = ReportStatus.RESOLVED
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
    }

    // TC1: DELETE_POST → adminCommunityService.deletePost(targetId) 호출 검증
    @Test
    fun `resolveReport with DELETE_POST should delete post and notify reporter`() {
        // Given
        val report = pendingPostReport(testUser, targetId = 100L)
        whenever(reportRepository.findById(1L)).thenReturn(Optional.of(report))

        val request = ResolveReportRequest(actionType = ReportActionType.DELETE_POST, adminNote = null)

        // When
        service.resolveReport(1L, adminId = 99L, request)

        // Then
        verify(adminCommunityService).deletePost(100L)
        verify(notificationService).notifyReportResolved(
            toUserId = 1L,
            actionLabel = "게시글 삭제"
        )
        assertEquals(ReportStatus.RESOLVED, report.status)
        assertEquals(99L, report.resolvedByAdminId)
    }

    // TC2: WARN_USER → verifyNoInteractions(adminCommunityService, adminUserManagementService, adminChatService) + 알림 발송 검증
    @Test
    fun `resolveReport with WARN_USER should not call any service and only notify reporter`() {
        // Given
        val report = pendingUserReport(testUser)
        whenever(reportRepository.findById(2L)).thenReturn(Optional.of(report))

        val request = ResolveReportRequest(actionType = ReportActionType.WARN_USER, adminNote = "경고: 부적절 언행")

        // When
        service.resolveReport(2L, adminId = 99L, request)

        // Then — WARN_USER는 제재 서비스 호출 없음
        verifyNoInteractions(adminCommunityService)
        verifyNoInteractions(adminUserManagementService)
        verifyNoInteractions(adminChatService)

        // 알림은 발송됨
        verify(notificationService).notifyReportResolved(
            toUserId = 1L,
            actionLabel = "경고 조치"
        )
        assertEquals(ReportActionType.WARN_USER, report.actionType)
        assertEquals("경고: 부적절 언행", report.adminNote)
    }

    // TC3: dismissReport → DISMISSED 상태 변경 + dismiss 알림 발송
    @Test
    fun `dismissReport should set DISMISSED status and notify reporter`() {
        // Given
        val report = pendingUserReport(testUser)
        whenever(reportRepository.findById(2L)).thenReturn(Optional.of(report))

        // When
        service.dismissReport(2L, adminId = 99L)

        // Then
        assertEquals(ReportStatus.DISMISSED, report.status)
        assertEquals(99L, report.resolvedByAdminId)
        verify(notificationService).notifyReportDismissed(toUserId = 1L)
    }

    // TC4: 타입 불일치 (POST 신고에 DELETE_COMMENT) → BadRequestException
    @Test
    fun `resolveReport with type mismatch should throw BadRequestException`() {
        // Given
        val report = pendingPostReport(testUser, targetId = 100L)
        whenever(reportRepository.findById(1L)).thenReturn(Optional.of(report))

        val request = ResolveReportRequest(actionType = ReportActionType.DELETE_COMMENT)

        // When & Then
        assertThrows<BadRequestException> {
            service.resolveReport(1L, adminId = 99L, request)
        }
        verifyNoInteractions(notificationService)
    }

    // TC5: 이미 RESOLVED 상태 → BadRequestException("이미 처리된 신고입니다")
    @Test
    fun `resolveReport on already resolved report should throw BadRequestException`() {
        // Given
        val report = resolvedReport()
        whenever(reportRepository.findById(3L)).thenReturn(Optional.of(report))

        val request = ResolveReportRequest(actionType = ReportActionType.WARN_USER)

        // When & Then
        val ex = assertThrows<BadRequestException> {
            service.resolveReport(3L, adminId = 99L, request)
        }
        assertEquals("이미 처리된 신고입니다", ex.message)
        verifyNoInteractions(notificationService)
    }

    // TC6: resolveReport 후 notification 호출 검증 (notifyReportResolved)
    @Test
    fun `resolveReport should call notifyReportResolved with correct arguments`() {
        // Given
        val report = pendingUserReport(testUser, targetId = 2L)
        whenever(reportRepository.findById(2L)).thenReturn(Optional.of(report))

        val request = ResolveReportRequest(actionType = ReportActionType.SUSPEND_USER, adminNote = "규정 위반")

        // When
        service.resolveReport(2L, adminId = 99L, request)

        // Then
        verify(notificationService).notifyReportResolved(
            toUserId = 1L,
            actionLabel = "계정 정지"
        )
    }

    // TC7: dismissReport 후 notification 호출 검증 (notifyReportDismissed)
    @Test
    fun `dismissReport should call notifyReportDismissed with correct userId`() {
        // Given
        val report = pendingPostReport(testUser)
        whenever(reportRepository.findById(1L)).thenReturn(Optional.of(report))

        // When
        service.dismissReport(1L, adminId = 99L)

        // Then
        verify(notificationService).notifyReportDismissed(toUserId = 1L)
        assertEquals(ReportStatus.DISMISSED, report.status)
    }
}
