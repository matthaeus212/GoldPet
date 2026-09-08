package com.goldpet.domain.community.service

import com.goldpet.domain.common.exception.*
import com.goldpet.domain.community.dto.CommunityCommentCreateRequest
import com.goldpet.domain.community.dto.CommunityPostCreateRequest
import com.goldpet.domain.community.dto.CommunityPostUpdateRequest
import com.goldpet.domain.community.entity.CommunityCategory
import com.goldpet.domain.community.entity.CommunityComment
import com.goldpet.domain.community.entity.CommunityPost
import com.goldpet.domain.community.entity.CommunityPostLike
import com.goldpet.domain.community.repository.*
import com.goldpet.domain.common.repository.FileAttachmentRepository
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.*

class CommunityServiceTest {

    @Mock
    private lateinit var postRepository: CommunityPostRepository

    @Mock
    private lateinit var categoryRepository: CommunityCategoryRepository

    @Mock
    private lateinit var commentRepository: CommunityCommentRepository

    @Mock
    private lateinit var likeRepository: CommunityPostLikeRepository

    @Mock
    private lateinit var commentLikeRepository: CommunityCommentLikeRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var fileRepository: FileAttachmentRepository

    @Mock
    private lateinit var systemSettingService: SystemSettingService

    @Mock
    private lateinit var badgeAwardService: com.goldpet.domain.gamification.service.BadgeAwardService

    @Mock
    private lateinit var userBlockRepository: com.goldpet.domain.friend.repository.UserBlockRepository

    @Mock
    private lateinit var fileAttachmentLookupService: com.goldpet.domain.common.service.FileAttachmentLookupService

    private lateinit var communityService: CommunityService

    private lateinit var testUser: User
    private lateinit var testCategory: CommunityCategory
    private lateinit var testPost: CommunityPost

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        communityService = CommunityService(
            postRepository,
            categoryRepository,
            commentRepository,
            likeRepository,
            commentLikeRepository,
            userRepository,
            fileRepository,
            systemSettingService,
            badgeAwardService,
            userBlockRepository,
            fileAttachmentLookupService
        )

        testUser = createTestUser(1L, "testuser", "테스트유저")

        testCategory = CommunityCategory(
            id = 1L,
            code = "FREE",
            name = "자유게시판"
        )

        testPost = CommunityPost(
            id = 1L,
            user = testUser,
            category = testCategory,
            title = "테스트 게시글",
            content = "테스트 내용입니다.",
            visibility = CommunityPost.Visibility.PUBLIC,
            postType = CommunityPost.PostType.GENERAL
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
    }

    private fun createTestUser(id: Long, oauthId: String, nickname: String): User {
        return User(
            id = id,
            email = "$oauthId@example.com",
            oauthProvider = "LOCAL",
            oauthId = oauthId,
            username = oauthId,
            password = "password",
            nickname = nickname,
            name = nickname,
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
    fun `getCategories should return all categories`() {
        // Given
        val categories = listOf(
            CommunityCategory(id = 1L, code = "FREE", name = "자유게시판"),
            CommunityCategory(id = 2L, code = "QNA", name = "질문게시판")
        )
        whenever(categoryRepository.findAll()).thenReturn(categories)
        whenever(postRepository.countByCategoryAndIsHiddenFalse(any())).thenReturn(10L)

        // When
        val result = communityService.getCategories()

        // Then
        assertEquals(2, result.size)
        assertEquals("자유게시판", result[0].name)
        assertEquals("질문게시판", result[1].name)
        assertEquals("FREE", result[0].code)
    }

    @Test
    fun `createPost should create new post`() {
        // Given
        val request = CommunityPostCreateRequest(
            categoryId = 1L,
            title = "새 게시글",
            content = "게시글 내용입니다.",
            imageUrls = emptyList(),
            visibility = CommunityPost.Visibility.PUBLIC,
            postType = CommunityPost.PostType.GENERAL
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory))
        whenever(postRepository.save(any<CommunityPost>())).thenAnswer { invocation ->
            val post = invocation.getArgument<CommunityPost>(0)
            CommunityPost(
                id = 1L,
                user = post.user,
                category = post.category,
                title = post.title,
                content = post.content,
                visibility = post.visibility,
                postType = post.postType
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = communityService.createPost(1L, request)

        // Then
        assertNotNull(result)
        assertEquals("새 게시글", result.title)
        assertEquals("게시글 내용입니다.", result.content)
    }

    @Test
    fun `createPost should throw exception when user not found`() {
        // Given
        val request = CommunityPostCreateRequest(
            categoryId = 1L,
            title = "새 게시글",
            content = "게시글 내용입니다."
        )
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            communityService.createPost(999L, request)
        }
    }

    @Test
    fun `createPost should throw exception when category not found`() {
        // Given
        val request = CommunityPostCreateRequest(
            categoryId = 999L,
            title = "새 게시글",
            content = "게시글 내용입니다."
        )
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(categoryRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            communityService.createPost(1L, request)
        }
    }

    @Test
    fun `getPostDetail should return post with details`() {
        // Given
        whenever(postRepository.findById(1L)).thenReturn(Optional.of(testPost))
        whenever(postRepository.save(any<CommunityPost>())).thenReturn(testPost)
        whenever(likeRepository.existsByPostAndUser(any(), any())).thenReturn(false)
        whenever(commentRepository.existsByPostIdAndUserId(any(), any())).thenReturn(false)
        whenever(commentRepository.countByPostId(1L)).thenReturn(0L)
        whenever(commentRepository.findByPostIdAndIsHiddenFalseOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))

        // When
        val result = communityService.getPostDetail(1L, 1L)

        // Then
        assertNotNull(result)
        assertEquals("테스트 게시글", result.title)
        assertEquals("테스트 내용입니다.", result.content)
    }

    @Test
    fun `getPostDetail should throw exception when post not found`() {
        // Given
        whenever(postRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            communityService.getPostDetail(999L, 1L)
        }
    }

    @Test
    fun `toggleLike should add like when not exists`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(postRepository.findById(1L)).thenReturn(Optional.of(testPost))
        whenever(likeRepository.findByPostAndUser(testPost, testUser)).thenReturn(null)
        whenever(likeRepository.save(any<CommunityPostLike>())).thenAnswer { it.getArgument(0) }

        // When
        val result = communityService.toggleLike(1L, 1L)

        // Then
        assertTrue(result)
    }

    @Test
    fun `toggleLike should remove like when exists`() {
        // Given
        val existingLike = CommunityPostLike(
            id = 1L,
            post = testPost,
            user = testUser
        )
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(postRepository.findById(1L)).thenReturn(Optional.of(testPost))
        whenever(likeRepository.findByPostAndUser(testPost, testUser)).thenReturn(existingLike)

        // When
        val result = communityService.toggleLike(1L, 1L)

        // Then
        assertFalse(result)
    }

    @Test
    fun `createComment should create new comment`() {
        // Given
        val request = CommunityCommentCreateRequest(
            content = "댓글 내용입니다.",
            parentCommentId = null
        )

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(postRepository.findById(1L)).thenReturn(Optional.of(testPost))
        whenever(commentRepository.save(any<CommunityComment>())).thenAnswer { invocation ->
            val comment = invocation.getArgument<CommunityComment>(0)
            CommunityComment(
                id = 1L,
                post = comment.post,
                user = comment.user,
                content = comment.content,
                parentComment = comment.parentComment
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = communityService.createComment(1L, 1L, request)

        // Then
        assertNotNull(result)
        assertEquals("댓글 내용입니다.", result.content)
    }

    @Test
    fun `updatePost should update post when user is author`() {
        // Given
        val request = CommunityPostUpdateRequest(
            title = "수정된 제목",
            content = "수정된 내용"
        )

        whenever(postRepository.findById(1L)).thenReturn(Optional.of(testPost))
        whenever(postRepository.save(any<CommunityPost>())).thenReturn(testPost)

        // When
        val result = communityService.updatePost(1L, 1L, request)

        // Then
        assertEquals("수정된 제목", result.title)
        assertEquals("수정된 내용", result.content)
    }

    @Test
    fun `updatePost should throw exception when user is not author`() {
        // Given
        val request = CommunityPostUpdateRequest(title = "수정된 제목")
        whenever(postRepository.findById(1L)).thenReturn(Optional.of(testPost))

        // When & Then - user 999 is not the author
        assertThrows<ForbiddenException> {
            communityService.updatePost(1L, 999L, request)
        }
    }

    @Test
    fun `deletePost should delete post when user is author`() {
        // Given
        whenever(postRepository.findById(1L)).thenReturn(Optional.of(testPost))

        // When & Then
        assertDoesNotThrow {
            communityService.deletePost(1L, 1L)
        }
    }

    @Test
    fun `deletePost should throw exception when user is not author`() {
        // Given
        whenever(postRepository.findById(1L)).thenReturn(Optional.of(testPost))

        // When & Then
        assertThrows<ForbiddenException> {
            communityService.deletePost(1L, 999L)
        }
    }

    @Test
    fun `getPosts should return paginated posts`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val posts = listOf(testPost)
        val page = PageImpl(posts, pageable, 1)

        whenever(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory))
        whenever(postRepository.findAllByCategoryAndIsHiddenFalse(testCategory, pageable)).thenReturn(page)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(likeRepository.existsByPostAndUser(any(), any())).thenReturn(false)
        whenever(commentRepository.existsByPostIdAndUserId(any(), any())).thenReturn(false)
        whenever(commentRepository.countByPostId(any())).thenReturn(0L)
        whenever(systemSettingService.getInt(any(), any())).thenReturn(100)

        // When
        val result = communityService.getPosts(1L, pageable, 1L)

        // Then
        assertEquals(1, result.content.size)
        assertEquals("테스트 게시글", result.content[0].title)
    }

    @Test
    fun `searchPosts should return matching posts`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val posts = listOf(testPost)
        val page = PageImpl(posts, pageable, 1)

        whenever(postRepository.searchByKeywordAndIsHiddenFalse("테스트", pageable)).thenReturn(page)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(likeRepository.existsByPostAndUser(any(), any())).thenReturn(false)

        // When
        val result = communityService.searchPosts("테스트", pageable, 1L)

        // Then
        assertEquals(1, result.content.size)
    }

    /**
     * PERF-004 회귀 테스트 — 피드가 게시글마다 좋아요/댓글 쿼리 3회(N*3) 대신
     * postIds IN 집계 3회(고정)로 동작함을 상호작용으로 실증한다.
     * 픽스 전 코드(existsByPostAndUser/existsByPostIdAndUserId/countByPostId per-post)에서는
     * never() 검증이 실패한다. 매핑 값(commentCount/isLiked/isCommented)도 배치 결과와 일치해야 한다.
     */
    @Test
    fun `getPosts batches like-comment aggregates instead of per-post queries (PERF-004)`() {
        // Given: 3개 게시글
        val pageable = PageRequest.of(0, 10)
        fun post(id: Long) = CommunityPost(
            id = id,
            user = testUser,
            category = testCategory,
            title = "글$id",
            content = "내용$id",
            visibility = CommunityPost.Visibility.PUBLIC,
            postType = CommunityPost.PostType.GENERAL
        ).apply { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now() }
        val posts = listOf(post(1L), post(2L), post(3L))
        val page = PageImpl(posts, pageable, 3)

        whenever(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory))
        whenever(postRepository.findAllByCategoryAndIsHiddenFalse(testCategory, pageable)).thenReturn(page)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(systemSettingService.getInt(any(), any())).thenReturn(100)
        // 배치 집계 스텁: post1→댓글2, post2→댓글5(post3 부재=0); 좋아요=post1; 내댓글=post2
        whenever(commentRepository.countByPostIdIn(any()))
            .thenReturn(listOf(arrayOf<Any>(1L, 2L), arrayOf<Any>(2L, 5L)))
        whenever(likeRepository.findLikedPostIds(any(), eq(1L))).thenReturn(listOf(1L))
        whenever(commentRepository.findCommentedPostIds(any(), eq(1L))).thenReturn(listOf(2L))

        // When
        val result = communityService.getPosts(1L, pageable, 1L)

        // Then: 매핑 값이 배치 결과와 일치
        val byId = result.content.associateBy { it.id }
        assertEquals(2L, byId[1L]!!.commentCount)
        assertTrue(byId[1L]!!.isLiked)
        assertFalse(byId[1L]!!.isCommentedByMe)
        assertEquals(5L, byId[2L]!!.commentCount)
        assertFalse(byId[2L]!!.isLiked)
        assertTrue(byId[2L]!!.isCommentedByMe)
        assertEquals(0L, byId[3L]!!.commentCount)

        // And: 배치 메서드는 각 1회, per-post N+1 메서드는 호출되지 않음
        verify(commentRepository).countByPostIdIn(any())
        verify(likeRepository).findLikedPostIds(any(), eq(1L))
        verify(commentRepository).findCommentedPostIds(any(), eq(1L))
        verify(commentRepository, never()).countByPostId(any())
        verify(likeRepository, never()).existsByPostAndUser(any(), any())
        verify(commentRepository, never()).existsByPostIdAndUserId(any(), any())
    }
}
