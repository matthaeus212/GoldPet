package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.ReportAdminResponse
import com.goldpet.domain.admin.controller.ResolveReportRequest
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.chat.repository.ChatMessageRepository
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.community.repository.CommunityCommentRepository
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.course.repository.WalkCourseRepository
import com.goldpet.domain.notification.service.NotificationService
import com.goldpet.domain.report.entity.Report
import com.goldpet.domain.report.entity.ReportActionType
import com.goldpet.domain.report.entity.ReportStatus
import com.goldpet.domain.report.entity.ReportType
import com.goldpet.domain.report.repository.ReportRepository
import com.goldpet.domain.walk.entity.WalkSpot
import com.goldpet.domain.walk.repository.WalkSpotRepository
import com.goldpet.domain.walk.service.PhotoUrlSigner
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class AdminReportService(
    private val reportRepository: ReportRepository,
    private val adminCommunityService: AdminCommunityService,
    private val adminUserManagementService: AdminUserManagementService,
    private val adminChatService: AdminChatService,
    private val adminUserRepository: AdminUserRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val communityCommentRepository: CommunityCommentRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val walkCourseRepository: WalkCourseRepository,
    private val notificationService: NotificationService,
    private val walkSpotRepository: WalkSpotRepository,
    private val photoUrlSigner: PhotoUrlSigner
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    // --- 단건 조회: resolvedByAdminId가 있으면 AdminUser.name 조회 ---
    fun getReport(reportId: Long): ReportAdminResponse {
        val report = reportRepository.findById(reportId)
            .orElseThrow { NotFoundException("Report not found: $reportId") }

        val resolvedByName = report.resolvedByAdminId?.let { adminId ->
            adminUserRepository.findById(adminId).orElse(null)?.name
        }

        val spot = if (report.type == ReportType.WALK_SPOT) {
            walkSpotRepository.findById(report.targetId).orElse(null)
        } else null

        return toResponse(report, resolvedByName, spot)
    }

    // --- 목록 조회: N+1 방지를 위해 adminId를 batch 조회 ---
    fun getReports(status: String?, pageable: Pageable): Page<ReportAdminResponse> {
        val reports = if (status != null) {
            val reportStatus = ReportStatus.valueOf(status)
            reportRepository.findByStatus(reportStatus, pageable)
        } else {
            reportRepository.findAll(pageable)
        }

        // N+1 방지: 페이지 내 resolvedByAdminId를 모아 한 번에 조회
        val adminIds = reports.content
            .mapNotNull { it.resolvedByAdminId }
            .distinct()
        val adminNameMap: Map<Long, String> = if (adminIds.isNotEmpty()) {
            adminUserRepository.findAllById(adminIds)
                .associate { it.id to it.name }
        } else {
            emptyMap()
        }

        // N+1 방지: WALK_SPOT 타입 신고의 spot을 한 번에 조회
        val walkSpotIds = reports.content
            .filter { it.type == ReportType.WALK_SPOT }
            .map { it.targetId }
            .distinct()
        val walkSpotMap: Map<Long, WalkSpot> = if (walkSpotIds.isNotEmpty()) {
            walkSpotRepository.findAllById(walkSpotIds)
                .associateBy { it.id }
        } else {
            emptyMap()
        }

        return reports.map { report ->
            val spot = if (report.type == ReportType.WALK_SPOT) walkSpotMap[report.targetId] else null
            toResponse(report, report.resolvedByAdminId?.let { adminNameMap[it] }, spot)
        }
    }

    private fun toResponse(report: Report, resolvedByName: String?, walkSpot: WalkSpot? = null): ReportAdminResponse {
        val rawKey = walkSpot?.imageUrl
        val signedUrl = rawKey?.let { photoUrlSigner.signedUrlOrNull(it) ?: it }
        return ReportAdminResponse(
            id = report.id,
            type = report.type,
            targetId = report.targetId,
            targetPreview = report.targetPreview,
            reason = report.reason,
            reporterNickname = report.reporter.nickname ?: "",
            reporterId = report.reporter.id,
            status = report.status,
            createdAt = report.createdAt.format(formatter),
            resolvedAt = report.resolvedAt?.format(formatter),
            actionType = report.actionType,
            adminNote = report.adminNote,
            resolvedByName = resolvedByName,
            targetImageUrl = signedUrl,
            targetImageKey = rawKey
        )
    }

    // --- 제재 처리 ---
    @Transactional
    fun resolveReport(reportId: Long, adminId: Long, request: ResolveReportRequest) {
        val report = reportRepository.findById(reportId)
            .orElseThrow { NotFoundException("Report not found: $reportId") }

        if (report.status != ReportStatus.PENDING) {
            throw BadRequestException("이미 처리된 신고입니다")
        }

        // 제재 실행
        executeAction(report, request.actionType)

        // 상태 업데이트
        report.status = ReportStatus.RESOLVED
        report.resolvedAt = LocalDateTime.now()
        report.resolvedByAdminId = adminId
        report.actionType = request.actionType
        report.adminNote = request.adminNote
        reportRepository.save(report)

        // @Transactional 컨텍스트이므로 report.reporter.id lazy loading 안전
        notificationService.notifyReportResolved(
            toUserId = report.reporter.id,
            actionLabel = request.actionType.label
        )
    }

    private fun executeAction(report: Report, actionType: ReportActionType) {
        when (actionType) {
            ReportActionType.DELETE_POST -> {
                if (report.type != ReportType.POST) throw BadRequestException("POST 신고만 게시글 삭제 가능")
                try {
                    adminCommunityService.deletePost(report.targetId)
                } catch (e: NotFoundException) {
                    // 이미 삭제된 경우 무시 (목적 달성)
                }
            }
            ReportActionType.DELETE_COMMENT -> {
                if (report.type != ReportType.COMMENT) throw BadRequestException("COMMENT 신고만 댓글 삭제 가능")
                try {
                    adminCommunityService.deleteComment(report.targetId)
                } catch (e: NotFoundException) {
                    // 이미 삭제된 경우 무시
                }
            }
            ReportActionType.DELETE_MESSAGE -> {
                if (report.type != ReportType.CHAT) throw BadRequestException("CHAT 신고만 메시지 삭제 가능")
                try {
                    adminChatService.deleteMessage(report.targetId)
                } catch (e: NotFoundException) {
                    // 이미 삭제된 경우 무시
                }
            }
            ReportActionType.SUSPEND_USER -> {
                val userId = getTargetUserId(report)
                adminUserManagementService.updateUserStatus(userId, UserStatus.SUSPENDED, "신고 처리에 의한 정지")
            }
            ReportActionType.WARN_USER -> {
                // 경고: 기록만 남김 (adminNote로 경고 내용 기재)
                // 향후 경고 횟수 누적/알림 시스템 연동 가능
            }
            ReportActionType.HIDE_POST -> {
                if (report.type != ReportType.POST) throw BadRequestException("POST 신고만 게시글 숨김 가능")
                adminCommunityService.hidePost(report.targetId, "신고 처리")
            }
            ReportActionType.HIDE_COMMENT -> {
                if (report.type != ReportType.COMMENT) throw BadRequestException("COMMENT 신고만 댓글 숨김 가능")
                adminCommunityService.hideComment(report.targetId, "신고 처리")
            }
            ReportActionType.HIDE_COURSE -> {
                if (report.type != ReportType.COURSE) throw BadRequestException("COURSE 신고만 코스 숨김 가능")
                val course = walkCourseRepository.findById(report.targetId)
                    .orElseThrow { NotFoundException("코스를 찾을 수 없습니다: ${report.targetId}") }
                course.isPublished = false
                walkCourseRepository.save(course)
            }
            ReportActionType.HIDE_CHAT -> {
                // V68 — Sprint 3 BLOCKER #5. ReportService.checkAutoSanction 자동 처리 + admin 수동 처리 모두 지원.
                if (report.type != ReportType.CHAT) throw BadRequestException("CHAT 신고만 메시지 숨김 가능")
                val message = chatMessageRepository.findById(report.targetId)
                    .orElseThrow { NotFoundException("메시지를 찾을 수 없습니다: ${report.targetId}") }
                if (message.hiddenAt == null) {
                    message.hide("admin 수동 hide (신고 처리)")
                    chatMessageRepository.save(message)
                }
            }
        }
    }

    /**
     * ReportType별 신고 대상의 작성자(사용자) ID를 조회한다.
     *
     * - USER: targetId가 곧 userId
     * - POST: targetId = community_posts.id -> CommunityPost.user.id
     * - COMMENT: targetId = community_comments.id -> CommunityComment.user.id
     * - CHAT: targetId = chat_messages.id -> ChatMessage.sender?.id
     */
    private fun getTargetUserId(report: Report): Long {
        return when (report.type) {
            ReportType.USER -> report.targetId
            ReportType.POST -> {
                val post = communityPostRepository.findById(report.targetId)
                    .orElseThrow { NotFoundException("게시글을 찾을 수 없습니다: ${report.targetId}") }
                post.user.id
            }
            ReportType.COMMENT -> {
                val comment = communityCommentRepository.findById(report.targetId)
                    .orElseThrow { NotFoundException("댓글을 찾을 수 없습니다: ${report.targetId}") }
                comment.user.id
            }
            ReportType.CHAT -> {
                val message = chatMessageRepository.findById(report.targetId)
                    .orElseThrow { NotFoundException("채팅 메시지를 찾을 수 없습니다: ${report.targetId}") }
                message.sender?.id
                    ?: throw BadRequestException("시스템 메시지는 사용자 정지 대상이 아닙니다")
            }
            ReportType.COURSE -> {
                val course = walkCourseRepository.findById(report.targetId)
                    .orElseThrow { NotFoundException("코스를 찾을 수 없습니다: ${report.targetId}") }
                course.author.id
            }
            ReportType.WALK_SPOT -> {
                val spot = walkSpotRepository.findById(report.targetId)
                    .orElseThrow { NotFoundException("산책 사진을 찾을 수 없습니다: ${report.targetId}") }
                spot.walk.user.id
            }
        }
    }

    // --- 기각 처리 ---
    @Transactional
    fun dismissReport(reportId: Long, adminId: Long) {
        val report = reportRepository.findById(reportId)
            .orElseThrow { NotFoundException("Report not found: $reportId") }
        report.status = ReportStatus.DISMISSED
        report.resolvedAt = LocalDateTime.now()
        report.resolvedByAdminId = adminId
        reportRepository.save(report)

        // @Transactional 컨텍스트이므로 report.reporter.id lazy loading 안전
        notificationService.notifyReportDismissed(report.reporter.id)
    }
}
