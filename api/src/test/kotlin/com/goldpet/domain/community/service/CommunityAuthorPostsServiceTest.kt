package com.goldpet.domain.community.service

import com.goldpet.domain.common.entity.FileAttachment
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.community.entity.CommunityPost
import com.goldpet.domain.community.entity.CommunityPostImage
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.Optional

class CommunityAuthorPostsServiceTest {

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var userBlockRepository: UserBlockRepository
    @Mock private lateinit var communityPostRepository: CommunityPostRepository

    private lateinit var service: CommunityAuthorPostsService

    private val viewerId = 10L
    private val authorId = 20L

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = CommunityAuthorPostsService(userRepository, userBlockRepository, communityPostRepository)
        // Default stubs shared across tests
        whenever(userRepository.findById(authorId)).thenReturn(Optional.of(activeUser(authorId)))
        whenever(userBlockRepository.existsByBlockerIdAndBlockedId(authorId, viewerId)).thenReturn(false)
        whenever(userBlockRepository.findBlockedUserIds(viewerId)).thenReturn(emptyList())
        whenever(communityPostRepository.findPublicByAuthorWithCursor(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun activeUser(id: Long): User = User(
        id = id,
        email = "u$id@goldpet.com",
        oauthProvider = "LOCAL",
        oauthId = "user$id",
        username = "user$id",
        password = "pass",
        nickname = "user$id",
        name = "user$id",
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

    private fun mockPost(
        id: Long = 1L,
        images: MutableList<CommunityPostImage> = mutableListOf(),
    ): CommunityPost {
        val post = mock<CommunityPost>()
        whenever(post.id).thenReturn(id)
        whenever(post.createdAt).thenReturn(LocalDateTime.of(2025, 1, 1, 12, 0, 0))
        whenever(post.images).thenReturn(images)
        return post
    }

    private fun fileAttachment(url: String, thumbUrl: String? = null, mediumUrl: String? = null, viewerUrl: String? = null) =
        FileAttachment(
            ownerUserId = authorId,
            fileType = "IMAGE",
            mimeType = "image/jpeg",
            url = url,
            thumbnailUrl = thumbUrl,
            mediumUrl = mediumUrl,
            viewerUrl = viewerUrl,
        )

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `findByAuthorId clamps size 150 to MAX_SIZE 100 and fetches 101 rows`() {
        val pageableCaptor: ArgumentCaptor<Pageable> = ArgumentCaptor.forClass(Pageable::class.java)

        service.findByAuthorId(viewerId = viewerId, authorId = authorId, cursor = null, size = 150)

        verify(communityPostRepository).findPublicByAuthorWithCursor(
            any(), any(), any(), any(), any(), capture(pageableCaptor)
        )
        assertEquals(101, pageableCaptor.value.pageSize,
            "Clamped size=100, fetch size must be effectiveSize+1=101")
    }

    @Test
    fun `findByAuthorId uses sentinel -1L as blockedUserIds when viewer has no blocks`() {
        @Suppress("UNCHECKED_CAST")
        val blockedCaptor: ArgumentCaptor<List<Long>> =
            ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Long>>

        whenever(userBlockRepository.findBlockedUserIds(viewerId)).thenReturn(emptyList())

        service.findByAuthorId(viewerId = viewerId, authorId = authorId, cursor = null, size = 10)

        verify(communityPostRepository).findPublicByAuthorWithCursor(
            any(), any(), any(), any(), capture(blockedCaptor), any()
        )
        assertEquals(listOf(-1L), blockedCaptor.value,
            "Empty block list must be replaced with sentinel [-1L] to avoid JPQL IN () error")
    }

    @Test
    fun `findByAuthorId falls back to original URL when file has no variant thumbnailUrl`() {
        val originalUrl = "https://cdn.goldpet.com/original.jpg"
        val file = fileAttachment(url = originalUrl, thumbUrl = null, mediumUrl = null, viewerUrl = null)
        val post = mockPost(id = 99L, images = mutableListOf(
            CommunityPostImage(post = mock(), file = file, sortOrder = 0)
        ))
        whenever(communityPostRepository.findPublicByAuthorWithCursor(any(), any(), any(), any(), any(), any()))
            .thenReturn(listOf(post))

        val result = service.findByAuthorId(viewerId = viewerId, authorId = authorId, cursor = null, size = 10)

        assertEquals(1, result.posts.size)
        val dto = result.posts[0]
        assertEquals(originalUrl, dto.thumbnailUrl,
            "thumbnailUrl must fall back to original URL when variant is null")
        assertEquals(originalUrl, dto.mediumUrl,
            "mediumUrl must fall back to original URL when variant is null")
        assertEquals(originalUrl, dto.viewerUrl,
            "viewerUrl must fall back to original URL when variant is null")
    }

    @Test
    fun `findByAuthorId throws BadRequestException for malformed cursor string`() {
        val ex = assertThrows<BadRequestException> {
            service.findByAuthorId(viewerId = viewerId, authorId = authorId, cursor = "!!not-base64!!", size = 10)
        }
        assertEquals("INVALID_CURSOR", ex.errorCode)
    }
}
