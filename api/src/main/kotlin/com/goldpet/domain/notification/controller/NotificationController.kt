package com.goldpet.domain.notification.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.notification.dto.NotificationResponse
import com.goldpet.domain.notification.dto.UnreadCountResponse
import com.goldpet.domain.notification.service.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {
    @Operation(summary = "알림 목록 조회")
    @GetMapping
    fun getNotifications(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) category: String?,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<NotificationResponse>> {
        return ResponseEntity.ok(notificationService.getNotifications(principal.id, category, pageable))
    }

    @Operation(summary = "읽지 않은 알림 수")
    @GetMapping("/unread-count")
    fun getUnreadCount(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<UnreadCountResponse> {
        val count = notificationService.getUnreadCount(principal.id)
        return ResponseEntity.ok(UnreadCountResponse(count))
    }

    @Operation(summary = "알림 읽음 처리")
    @PostMapping("/{notificationId}/read")
    fun markAsRead(
        @PathVariable notificationId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        notificationService.markAsRead(notificationId, principal.id)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "전체 읽음 처리")
    @PostMapping("/read-all")
    fun markAllAsRead(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Map<String, Int>> {
        val count = notificationService.markAllAsRead(principal.id)
        return ResponseEntity.ok(mapOf("markedCount" to count))
    }

    @Operation(summary = "알림 삭제")
    @DeleteMapping("/{notificationId}")
    fun deleteNotification(
        @PathVariable notificationId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        notificationService.deleteNotification(notificationId, principal.id)
        return ResponseEntity.noContent().build()
    }
}
