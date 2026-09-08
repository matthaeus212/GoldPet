package com.goldpet.domain.report.service

import com.goldpet.domain.admin.service.AdminCommunityService
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.community.repository.CommunityCommentRepository
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.course.repository.WalkCourseRepository
import com.goldpet.domain.report.dto.CreateReportRequest
import com.goldpet.domain.report.entity.Report
import com.goldpet.domain.report.entity.ReportStatus
import com.goldpet.domain.report.entity.ReportType
import com.goldpet.domain.report.repository.ReportRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.walk.repository.WalkSpotRepository
import com.goldpet.domain.common.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.*

class ReportServiceTest {

    @Mock
    private lateinit var reportRepository: ReportRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var postRepository: CommunityPostRepository

    @Mock
    private lateinit var commentRepository: CommunityCommentRepository

    @Mock
    private lateinit var walkCourseRepository: WalkCourseRepository

    @Mock
    private lateinit var chatMessageRepository: com.goldpet.domain.chat.repository.ChatMessageRepository

    @Mock
    private lateinit var systemSettingService: SystemSettingService

    @Mock
    private lateinit var adminCommunityService: AdminCommunityService

    @Mock
    private lateinit var walkSpotRepository: WalkSpotRepository

    private lateinit var reportService: ReportService

    private lateinit var testUser: User
    private lateinit var targetUser: User

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        reportService = ReportService(reportRepository, userRepository, postRepository, commentRepository, walkCourseRepository, chatMessageRepository, systemSettingService, adminCommunityService, walkSpotRepository)

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

        targetUser = User(
            id = 2L,
            email = "target@example.com",
            oauthProvider = "LOCAL",
            oauthId = "targetuser",
            username = "targetuser",
            password = "password",
            nickname = "피신고자",
            name = "대상",
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
    fun `createReport should create user report`() {
        // Given
        val request = CreateReportRequest(
            type = ReportType.USER,
            targetId = 2L,
            reason = "부적절한 행동"
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(2L)).thenReturn(Optional.of(targetUser))
        whenever(reportRepository.save(any<Report>())).thenAnswer { invocation ->
            invocation.getArgument<Report>(0).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = reportService.createReport(1L, request)

        // Then
        assertNotNull(result)
        assertEquals(ReportType.USER, result.type)
        assertEquals("부적절한 행동", result.reason)
        assertEquals(ReportStatus.PENDING, result.status)
    }

    @Test
    fun `createReport should throw exception when reporter not found`() {
        // Given
        val request = CreateReportRequest(
            type = ReportType.USER,
            targetId = 2L,
            reason = "부적절한 행동"
        )
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            reportService.createReport(999L, request)
        }
    }

    @Test
    fun `createReport should throw exception when target user not found for USER report`() {
        // Given
        val request = CreateReportRequest(
            type = ReportType.USER,
            targetId = 999L,
            reason = "부적절한 행동"
        )
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            reportService.createReport(1L, request)
        }
    }

    @Test
    fun `getMyReports should return user reports`() {
        // Given
        val report1 = Report(
            id = 1L,
            type = ReportType.USER,
            targetId = 2L,
            targetPreview = "피신고자",
            reason = "부적절한 행동",
            reporter = testUser,
            status = ReportStatus.PENDING
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        val report2 = Report(
            id = 2L,
            type = ReportType.POST,
            targetId = 100L,
            targetPreview = "게시글 제목",
            reason = "스팸",
            reporter = testUser,
            status = ReportStatus.RESOLVED
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }

        whenever(reportRepository.findAllByReporterId(1L)).thenReturn(listOf(report1, report2))

        // When
        val result = reportService.getMyReports(1L)

        // Then
        assertEquals(2, result.size)
        assertEquals(ReportType.USER, result[0].type)
        assertEquals(ReportType.POST, result[1].type)
    }

    @Test
    fun `getMyReports should return empty list when no reports`() {
        // Given
        whenever(reportRepository.findAllByReporterId(1L)).thenReturn(emptyList())

        // When
        val result = reportService.getMyReports(1L)

        // Then
        assertTrue(result.isEmpty())
    }
}
