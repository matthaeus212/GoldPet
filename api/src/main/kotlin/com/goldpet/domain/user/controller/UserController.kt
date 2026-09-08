package com.goldpet.domain.user.controller

import com.goldpet.common.net.TrustedClientIpResolver
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.user.dto.ChangePasswordRequest
import com.goldpet.domain.user.dto.DeleteAccountRequest
import com.goldpet.domain.user.dto.ProfileImageUpdateRequest
import com.goldpet.domain.user.dto.RegisterDeviceRequest
import com.goldpet.domain.user.dto.UserDeviceResponse
import com.goldpet.domain.user.dto.UserProfileUpdateRequest
import com.goldpet.domain.user.dto.UserResponse
import com.goldpet.domain.user.dto.FriendLocationResponse
import com.goldpet.domain.user.dto.NotificationSettingsRequest
import com.goldpet.domain.user.dto.NotificationSettingsResponse
import com.goldpet.domain.user.dto.PrivacySettingsRequest
import com.goldpet.domain.user.dto.PrivacySettingsResponse
import com.goldpet.domain.user.dto.UpdateNotificationRequest
import com.goldpet.domain.user.dto.UpdateFcmTokenRequest
import com.goldpet.domain.user.dto.UserStatsResponse
import com.goldpet.domain.user.entity.ConsentType
import com.goldpet.domain.user.service.ConsentHistoryResponse
import com.goldpet.domain.user.service.ConsentService
import com.goldpet.domain.user.service.UserDataExportService
import com.goldpet.domain.user.service.UserService
import com.goldpet.domain.walk.dto.BoundingBoxRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import com.goldpet.domain.user.entity.User
import com.goldpet.config.security.UserPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

data class RecordConsentRequest(
    val consentType: String,
    val isAgreed: Boolean,
    val version: String? = null
)

@Tag(name = "User", description = "사용자 프로필 및 계정 API")
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
    private val consentService: ConsentService,
    private val userDataExportService: UserDataExportService,
    private val fileAttachmentLookupService: FileAttachmentLookupService,
    private val trustedClientIpResolver: TrustedClientIpResolver
) {
    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    fun getMyProfile(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<UserResponse> {
        val userId = if (principal is com.goldpet.config.security.UserPrincipal) {
            principal.id
        } else {
            userService.getUserIdFromPrincipal(principal)
        }

        val user = userService.findUserById(userId)
        return if (user != null) {
            ResponseEntity.ok(UserResponse.from(user, fileAttachmentLookupService))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "내 프로필 수정")
    @RequestMapping(value = ["/me"], method = [RequestMethod.PUT, RequestMethod.PATCH])
    fun updateMyProfile(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody @Valid request: UserProfileUpdateRequest
    ): ResponseEntity<UserResponse> {
        val userId = userService.getUserIdFromPrincipal(principal)
        val updatedUser = userService.updateUserProfile(
            userId,
            request
        )
        return ResponseEntity.ok(UserResponse.from(updatedUser, fileAttachmentLookupService))
    }

    @Operation(summary = "내 계정 탈퇴")
    @DeleteMapping("/me")
    fun deleteMyAccount(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody(required = false) request: DeleteAccountRequest?
    ): ResponseEntity<Void> {
        val userId = userService.getUserIdFromPrincipal(principal)
        userService.deleteMyAccount(userId, request?.reason)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "프로필 이미지 수정")
    @PutMapping("/me/profile-image")
    fun updateMyProfileImage(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: ProfileImageUpdateRequest
    ): ResponseEntity<UserResponse> {
        val userId = userService.getUserIdFromPrincipal(principal)
        val updatedUser = userService.updateUserProfileImages(
            userId,
            request
        )
        return ResponseEntity.ok(UserResponse.from(updatedUser, fileAttachmentLookupService))
    }

    @Operation(summary = "알림 수신 여부 변경")
    @PatchMapping("/me/notification")
    fun updateNotification(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: UpdateNotificationRequest
    ): ResponseEntity<UserResponse> {
        val userId = userService.getUserIdFromPrincipal(principal)
        val updatedUser = userService.updateNotification(userId, request.enabled)
        return ResponseEntity.ok(UserResponse.from(updatedUser, fileAttachmentLookupService))
    }

    @Operation(summary = "알림 설정 조회")
    @GetMapping("/me/notification-settings")
    fun getNotificationSettings(
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<NotificationSettingsResponse> {
        val userId = userService.getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(userService.getNotificationSettings(userId))
    }

    @Operation(summary = "알림 설정 변경")
    @PutMapping("/me/notification-settings")
    fun updateNotificationSettings(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: NotificationSettingsRequest
    ): ResponseEntity<NotificationSettingsResponse> {
        val userId = userService.getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(userService.updateNotificationSettings(userId, request))
    }

    @Operation(summary = "개인정보 공개 설정 조회")
    @GetMapping("/me/privacy-settings")
    fun getPrivacySettings(
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<PrivacySettingsResponse> {
        val userId = userService.getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(userService.getPrivacySettings(userId))
    }

    @Operation(summary = "개인정보 공개 설정 변경")
    @PutMapping("/me/privacy-settings")
    fun updatePrivacySettings(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: PrivacySettingsRequest
    ): ResponseEntity<PrivacySettingsResponse> {
        val userId = userService.getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(userService.updatePrivacySettings(userId, request))
    }

    @Operation(summary = "FCM 토큰 등록/갱신")
    @PutMapping("/me/fcm-token")
    fun updateFcmToken(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: UpdateFcmTokenRequest
    ): ResponseEntity<Void> {
        val userId = userService.getUserIdFromPrincipal(principal)
        userService.updateFcmToken(userId, request.fcmToken)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "디바이스 등록")
    @PutMapping("/me/devices")
    fun registerDevice(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: RegisterDeviceRequest
    ): ResponseEntity<UserDeviceResponse> {
        val userId = userService.getUserIdFromPrincipal(principal)
        val device = userService.registerDevice(userId, request)
        return ResponseEntity.ok(
            UserDeviceResponse(
                id = device.id,
                deviceId = device.deviceId,
                deviceType = device.deviceType,
                deviceName = device.deviceName,
                appVersion = device.appVersion,
                lastLoginAt = device.lastLoginAt.toString()
            )
        )
    }

    @Operation(summary = "등록된 디바이스 목록 조회")
    @GetMapping("/me/devices")
    fun getActiveDevices(
        @AuthenticationPrincipal principal: UserDetails
    ): ResponseEntity<List<UserDeviceResponse>> {
        val userId = userService.getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(userService.getActiveDevices(userId))
    }

    @Operation(summary = "디바이스 등록 해제")
    @DeleteMapping("/me/devices/{deviceId}")
    fun deactivateDevice(
        @AuthenticationPrincipal principal: UserDetails,
        @PathVariable deviceId: String
    ): ResponseEntity<Void> {
        val userId = userService.getUserIdFromPrincipal(principal)
        userService.deactivateDevice(userId, deviceId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "비밀번호 변경")
    @PutMapping("/me/password")
    fun changePassword(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<Void> {
        val userId = userService.getUserIdFromPrincipal(principal)
        userService.changePassword(userId, request)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "영역 내 친구 위치 검색")
    @GetMapping("/search")
    fun searchFriendsInArea(
        @ModelAttribute @Valid request: BoundingBoxRequest
    ): ResponseEntity<List<FriendLocationResponse>> {
        val friendLocations = userService.findFriendsInArea(request)
        return ResponseEntity.ok(friendLocations)
    }

    @Operation(summary = "근처 친구 위치 검색")
    @GetMapping("/search/nearby")
    fun searchFriendsNearby(
        @RequestParam lat: Double,
        @RequestParam lon: Double,
        @RequestParam radius: Double
    ): ResponseEntity<List<FriendLocationResponse>> {
        val friendLocations = userService.findFriendsNearby(lat, lon, radius)
        return ResponseEntity.ok(friendLocations)
    }

    @Operation(summary = "관심사 목록 조회")
    @GetMapping("/interests")
    fun getAllInterests(): ResponseEntity<List<com.goldpet.domain.user.entity.Interest>> {
        return ResponseEntity.ok(userService.getAllInterests())
    }

    @Operation(summary = "취미 목록 조회")
    @GetMapping("/hobbies")
    fun getAllHobbies(): ResponseEntity<List<com.goldpet.domain.user.entity.Hobby>> {
        return ResponseEntity.ok(userService.getAllHobbies())
    }

    @Operation(summary = "닉네임 중복 확인")
    @GetMapping("/check-nickname")
    fun checkNicknameAvailability(@RequestParam nickname: String): ResponseEntity<Map<String, Boolean>> {
        val isAvailable = userService.isNicknameAvailable(nickname)
        return ResponseEntity.ok(mapOf("available" to isAvailable))
    }

    @Operation(summary = "동의 이력 조회")
    @GetMapping("/me/consents")
    fun getConsentHistory(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<List<ConsentHistoryResponse>> {
        val userId = userService.getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(consentService.getConsentHistory(userId))
    }

    @Operation(summary = "동의 이력 기록")
    @PostMapping("/me/consents")
    fun recordConsent(
        @AuthenticationPrincipal principal: UserDetails,
        @RequestBody request: RecordConsentRequest,
        httpRequest: jakarta.servlet.http.HttpServletRequest
    ): ResponseEntity<ConsentHistoryResponse> {
        val userId = userService.getUserIdFromPrincipal(principal)
        val consent = consentService.recordConsent(
            userId = userId,
            consentType = ConsentType.valueOf(request.consentType),
            isAgreed = request.isAgreed,
            version = request.version ?: "1.0",
            // 동의 이력은 법적 증빙이다. 위조 가능한 값을 사실처럼 남기느니 '모름'(null)이 낫다.
            ipAddress = trustedClientIpResolver.resolveOrNull(httpRequest)
        )
        return ResponseEntity.ok(ConsentHistoryResponse.from(consent))
    }

    @Operation(summary = "내 활동 통계 조회")
    @GetMapping("/me/stats")
    fun getMyStats(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<UserStatsResponse> {
        val userId = userService.getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(userService.getUserStats(userId))
    }

    @Operation(summary = "내 데이터 내보내기")
    @GetMapping("/me/data-export")
    fun exportMyData(@AuthenticationPrincipal principal: UserDetails): ResponseEntity<Map<String, Any?>> {
        val userId = userService.getUserIdFromPrincipal(principal)
        val data = userDataExportService.exportUserData(userId)
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=goldpet-data-export.json")
            .body(data)
    }
}
