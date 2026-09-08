package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.dto.*
import com.goldpet.domain.admin.entity.Banner
import com.goldpet.domain.admin.entity.BannerPlacement
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.admin.entity.Campaign
import com.goldpet.domain.admin.entity.NotificationTemplate
import com.goldpet.domain.admin.entity.PushDeliveryLog
import com.goldpet.domain.admin.repository.BannerRepository
import com.goldpet.domain.admin.repository.CampaignRepository
import com.goldpet.domain.admin.repository.NotificationTemplateRepository
import org.springframework.cache.annotation.CacheEvict
import com.goldpet.domain.admin.repository.PushDeliveryLogRepository
import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.notification.dto.CreateNotificationRequest
import com.goldpet.domain.notification.entity.NotificationType
import com.goldpet.domain.notification.service.NotificationService
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
@Transactional(readOnly = true)
class AdminMarketingService(
    private val bannerRepository: BannerRepository,
    private val campaignRepository: CampaignRepository,
    private val notificationTemplateRepository: NotificationTemplateRepository,
    private val pushDeliveryLogRepository: PushDeliveryLogRepository,
    private val notificationService: NotificationService,
    private val userRepository: UserRepository,
    private val fileAttachmentLookupService: FileAttachmentLookupService
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getBanners(placement: String? = null): List<BannerResponse> {
        val banners = if (placement != null) {
            val p = parsePlacement(placement)
            bannerRepository.findAllByPlacementOrderByDisplayOrderAsc(p)
        } else {
            bannerRepository.findAllByOrderByDisplayOrderAsc()
        }
        // T1-1.5 batch prefetch
        fileAttachmentLookupService.batchLookup(banners.mapNotNull { it.imageUrl })
        return banners.map {
            BannerResponse(
                id = it.id,
                title = it.title,
                imageUrl = it.imageUrl,
                imageUrlThumbnail = fileAttachmentLookupService.thumbnailUrlFor(it.imageUrl),
                imageUrlViewer = fileAttachmentLookupService.viewerUrlFor(it.imageUrl),
                link = it.link,
                isActive = it.isActive,
                displayOrder = it.displayOrder,
                placement = it.placement.name
            )
        }
    }

    @Transactional
    @CacheEvict(cacheNames = ["banners:public"], allEntries = true)
    fun createBanner(request: BannerCreateRequest): BannerResponse {
        val banner = bannerRepository.save(Banner(
            title = request.title,
            imageUrl = request.imageUrl,
            link = request.link,
            placement = parsePlacement(request.placement)
        ))
        return BannerResponse(
            id = banner.id,
            title = banner.title,
            imageUrl = banner.imageUrl,
            imageUrlThumbnail = fileAttachmentLookupService.thumbnailUrlFor(banner.imageUrl),
            imageUrlViewer = fileAttachmentLookupService.viewerUrlFor(banner.imageUrl),
            link = banner.link,
            isActive = banner.isActive,
            displayOrder = banner.displayOrder,
            placement = banner.placement.name
        )
    }

    @Transactional
    @CacheEvict(cacheNames = ["banners:public"], allEntries = true)
    fun updateBanner(bannerId: Long, request: BannerUpdateRequest): BannerResponse {
        val banner = bannerRepository.findById(bannerId)
            .orElseThrow { NotFoundException("Banner not found: $bannerId") }

        banner.title = request.title
        banner.imageUrl = request.imageUrl
        banner.link = request.link
        banner.isActive = request.isActive
        banner.displayOrder = request.displayOrder
        banner.placement = parsePlacement(request.placement)

        val saved = bannerRepository.save(banner)
        return BannerResponse(
            id = saved.id,
            title = saved.title,
            imageUrl = saved.imageUrl,
            imageUrlThumbnail = fileAttachmentLookupService.thumbnailUrlFor(saved.imageUrl),
            imageUrlViewer = fileAttachmentLookupService.viewerUrlFor(saved.imageUrl),
            link = saved.link,
            isActive = saved.isActive,
            displayOrder = saved.displayOrder,
            placement = saved.placement.name
        )
    }

    private fun parsePlacement(value: String): BannerPlacement =
        runCatching { BannerPlacement.valueOf(value.uppercase()) }
            .getOrElse { throw BadRequestException("유효하지 않은 placement 값입니다: $value (허용값: ${BannerPlacement.entries.joinToString()})") }

    @Transactional
    @CacheEvict(cacheNames = ["banners:public"], allEntries = true)
    fun deleteBanner(bannerId: Long) {
        bannerRepository.deleteById(bannerId)
    }

    fun getCampaigns(): List<CampaignResponse> {
        return campaignRepository.findAllByOrderByCreatedAtDesc().map {
            CampaignResponse(
                id = it.id,
                name = it.name,
                startDate = it.startDate.format(formatter),
                endDate = it.endDate.format(formatter),
                status = it.status,
                participants = it.participantCount
            )
        }
    }

    @Transactional
    fun createCampaign(request: CampaignCreateRequest): CampaignResponse {
        val campaign = campaignRepository.save(Campaign(
            name = request.name,
            startDate = LocalDateTime.parse(request.startDate + "T00:00:00"),
            endDate = LocalDateTime.parse(request.endDate + "T23:59:59")
        ))
        return CampaignResponse(
            id = campaign.id,
            name = campaign.name,
            startDate = campaign.startDate.format(formatter),
            endDate = campaign.endDate.format(formatter),
            status = campaign.status,
            participants = campaign.participantCount
        )
    }

    // Database-backed notification template methods
    fun getTemplates(): List<NotificationTemplateResponse> {
        return notificationTemplateRepository.findAll().map {
            NotificationTemplateResponse(
                id = it.id,
                title = it.titleTemplate,
                body = it.messageTemplate,
                category = it.type,
                createdAt = it.createdAt
            )
        }
    }

    @Transactional
    fun createTemplate(request: NotificationTemplateRequest): NotificationTemplateResponse {
        val template = NotificationTemplate(
            name = request.title,
            titleTemplate = request.title,
            messageTemplate = request.body,
            type = request.category,
            isActive = true
        )
        val saved = notificationTemplateRepository.save(template)
        return NotificationTemplateResponse(
            id = saved.id,
            title = saved.titleTemplate,
            body = saved.messageTemplate,
            category = saved.type,
            createdAt = saved.createdAt
        )
    }

    @Transactional
    fun updateTemplate(id: Long, request: NotificationTemplateRequest): NotificationTemplateResponse {
        val template = notificationTemplateRepository.findById(id)
            .orElseThrow { NotFoundException("Template not found: $id") }

        template.name = request.title
        template.titleTemplate = request.title
        template.messageTemplate = request.body
        template.type = request.category

        val updated = notificationTemplateRepository.save(template)
        return NotificationTemplateResponse(
            id = updated.id,
            title = updated.titleTemplate,
            body = updated.messageTemplate,
            category = updated.type,
            createdAt = updated.createdAt
        )
    }

    @Transactional
    fun deleteTemplate(id: Long) {
        notificationTemplateRepository.deleteById(id)
    }

    @Transactional
    fun sendNotification(request: SendNotificationRequest): SendNotificationResult {
        val template = request.templateId?.let {
            notificationTemplateRepository.findById(it).orElse(null)
        }

        val title = request.title ?: template?.titleTemplate ?: "알림"
        val message = request.body ?: template?.messageTemplate ?: ""
        val category = template?.type ?: "MARKETING"

        val notificationType = when (category) {
            "NOTICE" -> NotificationType.NOTICE
            "EVENT" -> NotificationType.EVENT
            "MARKETING" -> NotificationType.EVENT
            else -> NotificationType.SYSTEM
        }

        val targetUsers = when (request.targetType) {
            "ALL" -> userRepository.findAll()
            "USER" -> request.targetUserIds?.let { userIds ->
                userRepository.findAllById(userIds)
            } ?: emptyList()
            else -> emptyList()
        }

        var successCount = 0
        var failCount = 0

        targetUsers.forEach { user ->
            try {
                notificationService.createNotification(
                    CreateNotificationRequest(
                        userId = user.id,
                        type = notificationType,
                        title = title,
                        message = message,
                        targetId = null,
                        targetType = null,
                        senderId = null
                    )
                )
                successCount++
            } catch (e: Exception) {
                failCount++
            }
        }

        // FCM push는 NotificationService.createNotification() 내부에서 개별 전송됨

        val deliveryLog = PushDeliveryLog(
            template = template,
            title = title,
            message = message,
            targetType = request.targetType,
            targetValue = request.targetUserIds?.joinToString(","),
            targetCount = targetUsers.size,
            deliveredCount = successCount,
            failedCount = failCount,
            sentAt = LocalDateTime.now(),
            status = if (failCount == 0) "COMPLETED" else if (successCount == 0) "FAILED" else "COMPLETED",
            sentBy = "ADMIN"
        )
        pushDeliveryLogRepository.save(deliveryLog)

        return SendNotificationResult(successCount = successCount, failCount = failCount)
    }

    fun getDeliveryLogs(page: Int, size: Int): List<DeliveryLogResponse> {
        val pageable = PageRequest.of(page, size)
        return pushDeliveryLogRepository.findAllByOrderByCreatedAtDesc(pageable)
            .map { log ->
                DeliveryLogResponse(
                    id = log.id,
                    title = log.title,
                    targetType = log.targetType,
                    sentCount = log.deliveredCount,
                    sentAt = log.sentAt ?: log.createdAt
                )
            }.content
    }
}
