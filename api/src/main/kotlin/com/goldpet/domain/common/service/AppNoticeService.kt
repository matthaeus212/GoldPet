package com.goldpet.domain.common.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.goldpet.domain.common.dto.AppNoticeResponse
import com.goldpet.domain.common.dto.CreateAppNoticeRequest
import com.goldpet.domain.common.dto.UpdateAppNoticeRequest
import com.goldpet.domain.common.entity.AppNotice
import com.goldpet.domain.common.entity.AppNoticeType
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.common.repository.AppNoticeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class AppNoticeService(
    private val appNoticeRepository: AppNoticeRepository
) {
    private val objectMapper = jacksonObjectMapper()

    fun getActiveNotices(screen: String?, type: String? = null): List<AppNoticeResponse> {
        val now = LocalDateTime.now()
        if (type != null) {
            val noticeType = try {
                AppNoticeType.valueOf(type)
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }
            return appNoticeRepository.findActiveByType(noticeType, now)
                .map { it.toResponse() }
        }
        val targetScreen = screen ?: "ALL"
        return appNoticeRepository.findActiveNotices(now, targetScreen)
            .map { it.toResponse() }
    }

    fun getMaintenanceNotice(): AppNoticeResponse? {
        val now = LocalDateTime.now()
        return appNoticeRepository.findActiveByType(AppNoticeType.MAINTENANCE, now)
            .firstOrNull()
            ?.toResponse()
    }

    fun getAllNotices(): List<AppNoticeResponse> {
        return appNoticeRepository.findAll()
            .filter { it.isActive }
            .sortedByDescending { it.priority }
            .map { it.toResponse() }
    }

    @Transactional
    fun createNotice(request: CreateAppNoticeRequest): AppNoticeResponse {
        val notice = AppNotice(
            type = request.type,
            title = request.title,
            content = request.content,
            imageUrls = toJson(request.imageUrls),
            linkUrl = request.linkUrl,
            targetScreen = request.targetScreen ?: "ALL",
            priority = request.priority,
            isDismissible = request.isDismissible,
            startAt = request.startAt,
            endAt = request.endAt
        )
        return appNoticeRepository.save(notice).toResponse()
    }

    @Transactional
    fun updateNotice(id: Long, request: UpdateAppNoticeRequest): AppNoticeResponse {
        val notice = appNoticeRepository.findById(id)
            .orElseThrow { NotFoundException("공지사항을 찾을 수 없습니다: $id") }

        request.type?.let { notice.type = it }
        request.title?.let { notice.title = it }
        request.content?.let { notice.content = it }
        request.imageUrls?.let { notice.imageUrls = toJson(it) }
        request.linkUrl?.let { notice.linkUrl = it }
        request.targetScreen?.let { notice.targetScreen = it }
        request.priority?.let { notice.priority = it }
        request.isDismissible?.let { notice.isDismissible = it }
        request.isActive?.let { notice.isActive = it }
        request.startAt?.let { notice.startAt = it }
        request.endAt?.let { notice.endAt = it }

        return appNoticeRepository.save(notice).toResponse()
    }

    @Transactional
    fun deleteNotice(id: Long) {
        val notice = appNoticeRepository.findById(id)
            .orElseThrow { NotFoundException("공지사항을 찾을 수 없습니다: $id") }
        notice.isActive = false
        appNoticeRepository.save(notice)
    }

    private fun toJson(urls: List<String>?): String? {
        if (urls.isNullOrEmpty()) return null
        return objectMapper.writeValueAsString(urls)
    }

    private fun fromJson(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            objectMapper.readValue(json)
        } catch (_: Exception) {
            listOf(json)
        }
    }

    private fun AppNotice.toResponse() = AppNoticeResponse(
        id = id,
        type = type,
        title = title,
        content = content,
        imageUrls = fromJson(imageUrls),
        linkUrl = linkUrl,
        targetScreen = targetScreen,
        priority = priority,
        isDismissible = isDismissible,
        startAt = startAt,
        endAt = endAt,
        isActive = isActive
    )
}
