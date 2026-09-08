package com.goldpet.domain.user.service

import com.goldpet.domain.auth.repository.UserAuthProviderRepository
import com.goldpet.domain.friend.entity.LikeStatus
import com.goldpet.domain.friend.repository.LikeRepository
import com.goldpet.domain.friend.repository.MatchRepository
import com.goldpet.domain.user.entity.DeletedUser
import com.goldpet.domain.user.entity.DeviceType
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserDevice
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.event.UserDeletedEvent
import com.goldpet.domain.user.repository.DeletedUserRepository
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.user.repository.UserDeviceRepository
import com.goldpet.domain.user.dto.RegisterDeviceRequest
import com.goldpet.domain.user.dto.UserDeviceResponse
import com.goldpet.domain.walk.dto.BoundingBoxRequest
import com.goldpet.domain.user.dto.FriendLocationResponse
import com.goldpet.domain.user.dto.NotificationSettingsRequest
import com.goldpet.domain.user.dto.NotificationSettingsResponse
import com.goldpet.domain.user.dto.ChangePasswordRequest
import com.goldpet.domain.user.dto.PrivacySettingsRequest
import com.goldpet.domain.user.dto.PrivacySettingsResponse
import com.goldpet.domain.user.dto.ProfileImageUpdateRequest
import com.goldpet.domain.user.dto.UserStatsResponse
import com.goldpet.domain.user.entity.UserProfileImage
import com.goldpet.domain.common.exception.*
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val deletedUserRepository: DeletedUserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val userAuthProviderRepository: UserAuthProviderRepository,
    private val interestRepository: com.goldpet.domain.user.repository.InterestRepository,
    private val hobbyRepository: com.goldpet.domain.user.repository.HobbyRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val matchRepository: MatchRepository,
    private val likeRepository: LikeRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun findUserById(userId: Long): User? {
        return userRepository.findById(userId).orElse(null)
    }

    fun findUserByUsername(username: String): User? {
        return userRepository.findByUsername(username).orElse(null)
    }

    fun getUserIdFromPrincipal(principal: org.springframework.security.core.userdetails.UserDetails): Long {
        if (principal is com.goldpet.config.security.UserPrincipal) {
            return principal.id
        }
        // Fallback or AdminUserPrincipal handling if needed
        val user = userRepository.findByUsername(principal.username)
            .orElseThrow { NotFoundException("User not found") }
        return user.id
    }

    @Transactional
    fun createUser(oauthProvider: String, oauthId: String, nickname: String?): User {
        val user = User(
            oauthProvider = oauthProvider,
            oauthId = oauthId,
            nickname = nickname,
            email = null, // Email will be set later if provided by OAuth
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,

            profileImageUrl = null,
            username = null,
            password = null,
            name = null,
            birthDate = null,
            phoneNumber = null
        )
        return userRepository.save(user)
    }

    @Transactional
    fun updateNotification(userId: Long, enabled: Boolean): User {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }
        user.isNotificationEnabled = enabled
        return userRepository.save(user)
    }

    fun getNotificationSettings(userId: Long): NotificationSettingsResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }
        return NotificationSettingsResponse.from(user)
    }

    @Transactional
    fun updateNotificationSettings(userId: Long, request: NotificationSettingsRequest): NotificationSettingsResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }
        user.isNotificationEnabled = request.pushAlert
        user.isChatAlertEnabled = request.chatAlert
        user.isCommunityAlertEnabled = request.communityAlert
        user.isMarketingAlertEnabled = request.marketingAlert
        user.isReengagementAlertEnabled = request.reengagementAlert
        userRepository.save(user)
        return NotificationSettingsResponse.from(user)
    }

    @Transactional
    fun updateFcmToken(userId: Long, fcmToken: String?): User {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }
        user.fcmToken = fcmToken
        userRepository.save(user)
        // Also register/update legacy device entry for backward compatibility
        val legacyDeviceId = "legacy-$userId"
        registerDevice(userId, RegisterDeviceRequest(
            deviceId = legacyDeviceId,
            fcmToken = fcmToken,
            deviceType = DeviceType.UNKNOWN
        ))
        return user
    }

    @Transactional
    fun registerDevice(userId: Long, request: RegisterDeviceRequest): UserDevice {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }
        val existing = userDeviceRepository.findByUserIdAndDeviceId(userId, request.deviceId)
        if (existing.isPresent) {
            val device = existing.get()
            device.fcmToken = request.fcmToken
            device.deviceType = request.deviceType
            device.deviceName = request.deviceName
            device.appVersion = request.appVersion
            device.lastLoginAt = LocalDateTime.now()
            device.isActive = true
            val saved = userDeviceRepository.save(device)
            // Belt-and-suspenders: deactivate other users' devices with same FCM token
            if (request.fcmToken != null) {
                userDeviceRepository.deactivateByFcmTokenExcludingUser(request.fcmToken, userId)
            }
            return saved
        }
        // Check active device limit (max 5)
        if (userDeviceRepository.countByUserIdAndIsActiveTrue(userId) >= 5) {
            val oldest = userDeviceRepository.findOldestActiveByUserId(userId).firstOrNull()
            if (oldest != null) {
                oldest.isActive = false
                userDeviceRepository.save(oldest)
            }
        }
        val device = UserDevice(
            user = user,
            deviceId = request.deviceId,
            fcmToken = request.fcmToken,
            deviceType = request.deviceType,
            deviceName = request.deviceName,
            appVersion = request.appVersion,
            isActive = true,
            lastLoginAt = LocalDateTime.now()
        )
        val saved = userDeviceRepository.save(device)

        // Belt-and-suspenders: deactivate any other user's devices with the same FCM token
        // This prevents stale tokens from sending push notifications to the wrong user
        if (request.fcmToken != null) {
            userDeviceRepository.deactivateByFcmTokenExcludingUser(request.fcmToken, userId)
        }

        return saved
    }

    fun getActiveDevices(userId: Long): List<UserDeviceResponse> {
        return userDeviceRepository.findAllByUserIdAndIsActiveTrue(userId).map { device ->
            UserDeviceResponse(
                id = device.id,
                deviceId = device.deviceId,
                deviceType = device.deviceType,
                deviceName = device.deviceName,
                appVersion = device.appVersion,
                lastLoginAt = device.lastLoginAt.toString()
            )
        }
    }

    @Transactional
    fun deactivateDevice(userId: Long, deviceId: String) {
        userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId).ifPresent { device ->
            device.isActive = false
            userDeviceRepository.save(device)
        }
    }

    @Transactional
    fun updateUserProfile(userId: Long, request: com.goldpet.domain.user.dto.UserProfileUpdateRequest): User {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }

        // Immutability check: reject changes to locked fields after profile is verified
        if (user.profileLockedAt != null) {
            if (request.gender != null && request.gender != user.gender)
                throw ForbiddenException("성별은 인증 후 변경할 수 없습니다", errorCode = "PROFILE_LOCKED")
            if (request.birthDate != null && request.birthDate != user.birthDate?.toString())
                throw ForbiddenException("생년월일은 인증 후 변경할 수 없습니다", errorCode = "PROFILE_LOCKED")
            // Normalize phone numbers (strip non-digits) before comparing to handle format differences
            if (request.phoneNumber != null && request.phoneNumber.replace(Regex("[^0-9]"), "") != user.phoneNumber?.replace(Regex("[^0-9]"), ""))
                throw ForbiddenException("전화번호는 인증 후 변경할 수 없습니다", errorCode = "PROFILE_LOCKED")
            if (request.name != null && request.name.trim() != user.name?.trim())
                throw ForbiddenException("이름은 인증 후 변경할 수 없습니다", errorCode = "PROFILE_LOCKED")
        }

        val wasPhoneNull = user.phoneNumber == null
        request.name?.let { user.name = it }
        request.nickname?.let { user.nickname = it }
        request.birthDate?.let { user.birthDate = java.time.LocalDate.parse(it) }
        request.phoneNumber?.let { user.phoneNumber = it }
        request.gender?.let { user.gender = it }
        request.hasPet?.let { user.hasPet = it }
        request.intro?.let { user.intro = it }
        request.mbti?.let { user.mbti = it }

        // Lock profile on first-time phone number set
        if (request.phoneNumber != null && wasPhoneNull && user.profileLockedAt == null) {
            user.profileLockedAt = LocalDateTime.now()
        }

        // Update location
        if (request.mainLocationLat != null && request.mainLocationLng != null) {
            val geometryFactory = GeometryFactory(PrecisionModel(), 4326)
            user.mainLocationGeom = geometryFactory.createPoint(
                Coordinate(request.mainLocationLng, request.mainLocationLat)
            )
        }
        request.mainLocationText?.let { user.mainLocationText = it }

        // Update tags
        if (request.interests.isNotEmpty()) {
            val interests = interestRepository.findByNameIn(request.interests)
            user.interests.clear()
            user.interests.addAll(interests)
        }
        
        if (request.hobbies.isNotEmpty()) {
            val hobbies = hobbyRepository.findByNameIn(request.hobbies)
            user.hobbies.clear()
            user.hobbies.addAll(hobbies)
        }

        return userRepository.save(user)
    }

    @Cacheable("user:interests")
    fun getAllInterests(): List<com.goldpet.domain.user.entity.Interest> {
        return interestRepository.findAllByOrderByOrderIndexAsc()
    }

    @Cacheable("user:hobbies")
    fun getAllHobbies(): List<com.goldpet.domain.user.entity.Hobby> {
        return hobbyRepository.findAllByOrderByOrderIndexAsc()
    }

    @Transactional
    fun updateUserProfileImages(userId: Long, request: ProfileImageUpdateRequest): User {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }
        
        request.profileImageUrl?.let {
            user.profileImageUrl = it
            if (user.profileImages.isEmpty()) {
                user.profileImages.add(UserProfileImage(user = user, imageUrl = it, orderIndex = 0))
            } else {
                user.profileImages[0].imageUrl = it
            }
        }
        request.profileImageUrls?.takeIf { it.isNotEmpty() }?.let { urls ->
            user.profileImages.clear()
            urls.forEachIndexed { index, url ->
                user.profileImages.add(UserProfileImage(user = user, imageUrl = url, orderIndex = index))
            }
            user.profileImageUrl = urls.first()
        }
        
        return userRepository.save(user)
    }

    fun findFriendsInArea(request: BoundingBoxRequest): List<FriendLocationResponse> {
        val users = userRepository.findUsersWithinBoundingBox(
            request.minLat,
            request.minLon,
            request.maxLat,
            request.maxLon
        )
        return users.map { FriendLocationResponse.from(it) }
    }

    fun findFriendsNearby(lat: Double, lon: Double, radius: Double): List<FriendLocationResponse> {
        val users = userRepository.findUsersWithinRadius(lat, lon, radius)
        return users.map { user ->
            val userLat = user.mainLocationGeom?.y ?: 0.0
            val userLon = user.mainLocationGeom?.x ?: 0.0
            val distance = calculateDistancedInMeters(lat, lon, userLat, userLon)
            FriendLocationResponse.from(user, distance)
        }
    }

    private fun calculateDistancedInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    fun getPrivacySettings(userId: Long): PrivacySettingsResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }
        return PrivacySettingsResponse(
            isLocationSharingEnabled = user.isLocationSharingEnabled,
            isProfilePublic = user.isProfilePublic
        )
    }

    @Transactional
    fun updatePrivacySettings(userId: Long, request: PrivacySettingsRequest): PrivacySettingsResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found with id: $userId") }
        request.isLocationSharingEnabled?.let { user.isLocationSharingEnabled = it }
        request.isProfilePublic?.let { user.isProfilePublic = it }
        userRepository.save(user)
        return PrivacySettingsResponse(
            isLocationSharingEnabled = user.isLocationSharingEnabled,
            isProfilePublic = user.isProfilePublic
        )
    }

    fun isNicknameAvailable(nickname: String): Boolean {
        return !userRepository.findByNickname(nickname).isPresent
    }

    // 30초 TTL 자연 만료에 의존 (match/like 이벤트 시 명시적 CacheEvict 없음 — 의도적 설계)
    @Cacheable(value = ["user:stats"], key = "#userId")
    fun getUserStats(userId: Long): UserStatsResponse {
        val matchingCount = matchRepository.countByUserId(userId)
        val likesCount = likeRepository.countByToUserIdAndStatus(userId, LikeStatus.ACTIVE)
        return UserStatsResponse(
            matchingCount = matchingCount,
            likesCount = likesCount,
            friendsCount = matchingCount
        )
    }

    @Transactional
    fun changePassword(userId: Long, request: ChangePasswordRequest) {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("사용자를 찾을 수 없습니다.") }

        if (user.oauthProvider != "LOCAL") {
            throw BadRequestException("소셜 로그인 사용자는 비밀번호를 변경할 수 없습니다.")
        }

        if (user.password == null) {
            throw BadRequestException("비밀번호가 설정되지 않은 계정입니다.")
        }

        if (!passwordEncoder.matches(request.currentPassword, user.password)) {
            throw BadRequestException("현재 비밀번호가 일치하지 않습니다.")
        }

        val passwordRegex = Regex("^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#\$%^&*()_+\\-=\\[\\]{}|;:',.<>?/~`]).{8,}$")
        if (!passwordRegex.matches(request.newPassword)) {
            throw BadRequestException("비밀번호는 8자 이상이며, 영문, 숫자, 특수문자를 포함해야 합니다.")
        }

        if (passwordEncoder.matches(request.newPassword, user.password)) {
            throw BadRequestException("새 비밀번호는 현재 비밀번호와 달라야 합니다.")
        }

        user.password = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)
    }

    /**
     * Self-deletion: anonymize PII and publish UserDeletedEvent for cross-domain cleanup.
     */
    @Transactional
    fun deleteMyAccount(userId: Long, reason: String?) {
        anonymizeAndDelete(userId, reason, deletedBy = "SELF")
    }

    /**
     * Admin-initiated deletion: same anonymization with ADMIN attribution.
     */
    @Transactional
    fun adminDeleteAccount(userId: Long, reason: String?) {
        anonymizeAndDelete(userId, reason, deletedBy = "ADMIN")
    }

    private fun anonymizeAndDelete(userId: Long, reason: String?, deletedBy: String) {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found with id: $userId") }

        // Anonymize PII fields
        user.email = "deleted_${userId}@withdrawn.goldpet.com"
        user.emailHash = null
        user.nickname = "탈퇴한 사용자"
        user.name = null
        user.phoneNumber = null
        user.birthDate = null
        user.birthYear = null
        user.mainLocationGeom = null
        user.mainLocationText = null
        user.profileImageUrl = null
        user.fcmToken = null
        user.intro = null
        user.mbti = null
        user.gender = null
        user.password = null

        // Hash oauthId for re-signup prevention
        user.oauthId = sha256(user.oauthId)

        // Mark as withdrawn
        user.isActive = false
        user.status = UserStatus.WITHDRAWN

        // Clear collections
        user.profileImages.clear()
        user.interests.clear()
        user.hobbies.clear()

        userRepository.save(user)

        // Delete all registered devices
        userDeviceRepository.deleteAllByUserId(userId)

        // Delete all linked OAuth providers
        userAuthProviderRepository.deleteAllByUserId(userId)

        // Record deletion
        deletedUserRepository.save(
            DeletedUser(
                userId = userId,
                reason = reason,
                deletedBy = deletedBy
            )
        )

        // Publish event for cross-domain anonymization
        eventPublisher.publishEvent(UserDeletedEvent(this, userId))
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 저장된 메인 위치 좌표(lat, lng). 사용자가 없거나 위치 미설정이면 (0.0, 0.0).
     *
     * ARCH-005: FriendController/LikeController 가 이 단건 조회 때문에 UserRepository 를
     * 직접 주입받고 있었다. 호출부는 (0,0)을 "위치 없음"으로 보고 각자 기본값으로 대체한다.
     */
    @Transactional(readOnly = true)
    fun getMainCoordinates(userId: Long): Pair<Double, Double> {
        val geom = userRepository.findById(userId).orElse(null)?.mainLocationGeom
            ?: return 0.0 to 0.0
        return geom.y to geom.x
    }
}
