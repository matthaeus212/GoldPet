package com.goldpet.domain.user.service

import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.community.entity.CommunityPost
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.dto.PublicUserProfileResponse
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.Optional

class UserPublicProfileServiceTest {

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var userBlockRepository: UserBlockRepository
    @Mock private lateinit var communityPostRepository: CommunityPostRepository
    @Mock private lateinit var fileAttachmentLookupService: FileAttachmentLookupService
    @Mock private lateinit var petRepository: PetRepository

    private lateinit var service: UserPublicProfileService

    private val viewerId = 10L
    private val targetId = 20L

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = UserPublicProfileService(
            userRepository,
            userBlockRepository,
            communityPostRepository,
            fileAttachmentLookupService,
            petRepository,
        )
        // Safe defaults: no block relationships, no file variants, no pets
        whenever(fileAttachmentLookupService.thumbnailUrlFor(any())).thenReturn(null)
        whenever(fileAttachmentLookupService.viewerUrlFor(any())).thenReturn(null)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun activeUser(id: Long, nickname: String = "user$id"): User = User(
        id = id,
        email = "u$id@goldpet.com",
        oauthProvider = "LOCAL",
        oauthId = "user$id",
        username = "user$id",
        password = "pass",
        nickname = nickname,
        name = nickname,
        birthDate = null,
        phoneNumber = null,
        gender = null,
        birthYear = null,
        mainLocationText = null,
        mainLocationGeom = null,
        profileImageUrl = null,
        status = UserStatus.ACTIVE,
        isActive = true,
    )

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `getPublicProfile returns NORMAL status and postCount for active unblocked target`() {
        val target = activeUser(targetId, "고양이왕")
        whenever(userRepository.findById(targetId)).thenReturn(Optional.of(target))
        whenever(userBlockRepository.existsByBlockerIdAndBlockedId(targetId, viewerId)).thenReturn(false)
        whenever(userBlockRepository.existsByBlockerIdAndBlockedId(viewerId, targetId)).thenReturn(false)
        whenever(
            communityPostRepository.countByUserIdAndVisibilityAndIsHiddenFalse(
                targetId, CommunityPost.Visibility.PUBLIC
            )
        ).thenReturn(7L)

        val result = service.getPublicProfile(viewerId, targetId)

        assertEquals(targetId, result.userId)
        assertEquals("고양이왕", result.nickname)
        assertEquals(7L, result.publicPostCount)
        assertEquals(PublicUserProfileResponse.ProfileStatus.NORMAL, result.status)
        assertFalse(result.isMe)
        assertFalse(result.blockedByMe)
    }

    @Test
    fun `getPublicProfile returns BLOCKED_BY_ME with anonymized fields when viewer has blocked target`() {
        val target = activeUser(targetId)
        whenever(userRepository.findById(targetId)).thenReturn(Optional.of(target))
        // target has NOT blocked viewer, but viewer HAS blocked target
        whenever(userBlockRepository.existsByBlockerIdAndBlockedId(targetId, viewerId)).thenReturn(false)
        whenever(userBlockRepository.existsByBlockerIdAndBlockedId(viewerId, targetId)).thenReturn(true)

        val result = service.getPublicProfile(viewerId, targetId)

        assertEquals(PublicUserProfileResponse.ProfileStatus.BLOCKED_BY_ME, result.status)
        assertNull(result.nickname, "Nickname must be anonymized for BLOCKED_BY_ME")
        assertNull(result.profileUrl, "profileUrl must be anonymized for BLOCKED_BY_ME")
        assertNull(result.profileUrlThumbnail, "profileUrlThumbnail must be anonymized for BLOCKED_BY_ME")
        assertNull(result.profileUrlViewer, "profileUrlViewer must be anonymized for BLOCKED_BY_ME")
        assertEquals(0L, result.publicPostCount, "publicPostCount must be 0 for BLOCKED_BY_ME")
        assertTrue(result.blockedByMe)
        assertFalse(result.isMe)
    }

    @Test
    fun `getPublicProfile throws NotFoundException when target has blocked the viewer`() {
        val target = activeUser(targetId)
        whenever(userRepository.findById(targetId)).thenReturn(Optional.of(target))
        // target blocks viewer → BLOCKED_ME → generic 404
        whenever(userBlockRepository.existsByBlockerIdAndBlockedId(targetId, viewerId)).thenReturn(true)

        val ex = assertThrows<NotFoundException> {
            service.getPublicProfile(viewerId, targetId)
        }
        assertEquals("USER_NOT_FOUND_OR_BLOCKED", ex.errorCode)
        assertEquals(UserPublicProfileService.GENERIC_NOT_FOUND_MESSAGE, ex.message)
    }

    @Test
    fun `getPublicProfile throws NotFoundException when target is WITHDRAWN`() {
        val deactivated = activeUser(targetId).apply { status = UserStatus.WITHDRAWN }
        whenever(userRepository.findById(targetId)).thenReturn(Optional.of(deactivated))

        val ex = assertThrows<NotFoundException> {
            service.getPublicProfile(viewerId, targetId)
        }
        assertEquals("USER_NOT_FOUND_OR_BLOCKED", ex.errorCode)
    }

    @Test
    fun `getPublicProfile returns isMe=true when viewer requests their own profile`() {
        val self = activeUser(viewerId, "나자신")
        whenever(userRepository.findById(viewerId)).thenReturn(Optional.of(self))
        whenever(
            communityPostRepository.countByUserIdAndVisibilityAndIsHiddenFalse(
                viewerId, CommunityPost.Visibility.PUBLIC
            )
        ).thenReturn(3L)

        val result = service.getPublicProfile(viewerId, viewerId) // same id

        assertTrue(result.isMe)
        assertEquals(PublicUserProfileResponse.ProfileStatus.NORMAL, result.status)
        assertFalse(result.blockedByMe)
    }

    @Test
    fun `PublicUserProfileResponse DTO fields contain no PII field names`() {
        val piiFields = setOf(
            "phoneNumber", "email", "emailHash", "birthDate", "birthYear",
            "gender", "name", "password", "fcmToken", "mainLocationGeom",
            "mainLocationText", "profileLockedAt", "oauthProvider", "oauthId",
        )
        val dtoFields = PublicUserProfileResponse::class.java.declaredFields.map { it.name }.toSet()
        val leaked = dtoFields.intersect(piiFields)
        assertTrue(leaked.isEmpty()) {
            "PublicUserProfileResponse must not expose PII field(s): $leaked"
        }
    }
}
