package com.goldpet.domain.community.service

import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.common.util.toHttps
import com.goldpet.domain.community.dto.CommunityCategoryResponse
import com.goldpet.domain.community.dto.CommunityCommentCreateRequest
import com.goldpet.domain.community.dto.CommunityCommentResponse
import com.goldpet.domain.community.dto.CommunityPostCreateRequest
import com.goldpet.domain.community.dto.CommunityPostResponse
import com.goldpet.domain.community.dto.CommunityPostUpdateRequest
import com.goldpet.domain.community.entity.CommunityComment
import com.goldpet.domain.community.entity.CommunityPost
import com.goldpet.domain.community.entity.CommunityPostLike
import com.goldpet.domain.community.entity.CommunityPostImage
import com.goldpet.domain.community.repository.CommunityCategoryRepository
import com.goldpet.domain.community.repository.CommunityCommentRepository
import com.goldpet.domain.community.repository.CommunityPostLikeRepository
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.friend.repository.UserBlockRepository
import com.goldpet.domain.gamification.entity.BadgeConditionType
import com.goldpet.domain.gamification.service.BadgeAwardService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.common.exception.*
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CommunityService(
    private val postRepository: CommunityPostRepository,
    private val categoryRepository: CommunityCategoryRepository,
    private val commentRepository: CommunityCommentRepository,
    private val likeRepository: CommunityPostLikeRepository,
    private val commentLikeRepository: com.goldpet.domain.community.repository.CommunityCommentLikeRepository,
    private val userRepository: UserRepository,
    private val fileRepository: com.goldpet.domain.common.repository.FileAttachmentRepository,
    private val systemSettingService: com.goldpet.domain.common.service.SystemSettingService,
    private val badgeAwardService: BadgeAwardService,
    private val userBlockRepository: UserBlockRepository,
    private val fileAttachmentLookupService: FileAttachmentLookupService
) {
    private val log = LoggerFactory.getLogger(CommunityService::class.java)

    // T1-1.3 helpers — 각 mapping 에서 author profile variant 를 간결하게 공급.
    private fun thumbOf(url: String?): String? = fileAttachmentLookupService.thumbnailUrlFor(url)
    private fun viewerOf(url: String?): String? = fileAttachmentLookupService.viewerUrlFor(url)

    // T1-2 Phase 5: post body 이미지 variant 4종을 한 번에 추출. post.images 는 이미
    // FileAttachment join 으로 로드되어 있으므로 variant 컬럼 직접 접근 (추가 쿼리 0).
    private data class PostImageUrls(
        val originals: List<String>,
        val thumbnails: List<String>,
        val mediums: List<String>,
        val viewers: List<String>,
        val thumbnailWebps: List<String?>,
    )

    private fun postImageUrls(post: CommunityPost): PostImageUrls {
        val sorted = post.images.sortedBy { it.sortOrder }
        return PostImageUrls(
            originals = sorted.map { it.file.url },
            thumbnails = sorted.map { it.file.thumbnailUrl ?: it.file.url },
            mediums = sorted.map { it.file.mediumUrl ?: it.file.url },
            viewers = sorted.map { it.file.viewerUrl ?: it.file.url },
            thumbnailWebps = sorted.map { it.file.thumbnailUrlWebp },
        )
    }

    // 리스트 경로 전용: author 프로필 + post image 를 한 번의 IN 쿼리로 prefetch.
    private fun warmAuthorAndImages(posts: List<CommunityPost>) {
        val urls = posts.flatMap { post ->
            val u = post.user
            listOfNotNull(u.profileImageUrl.toHttps()) +
                post.images.map { it.file.url }
        }
        if (urls.isNotEmpty()) fileAttachmentLookupService.batchLookup(urls)
    }

    private fun warmCommentAuthors(comments: List<CommunityComment>) {
        val urls = comments.mapNotNull { it.user.profileImageUrl.toHttps() }
        if (urls.isNotEmpty()) fileAttachmentLookupService.batchLookup(urls)
    }

    // PERF-004: 리스트 경로 좋아요/댓글 집계 배치. 게시글마다 exists/count 3쿼리(N*3)를
    // postIds IN 집계 3쿼리(고정)로 대체. currentUserId 가 null 이면 좋아요/댓글여부는 빈 Set.
    private data class PostInteractionAggregates(
        val commentCountByPost: Map<Long, Long>,
        val likedPostIds: Set<Long>,
        val commentedPostIds: Set<Long>,
    ) {
        fun commentCount(postId: Long): Long = commentCountByPost[postId] ?: 0L
        fun isLiked(postId: Long): Boolean = postId in likedPostIds
        fun isCommented(postId: Long): Boolean = postId in commentedPostIds
    }

    private fun aggregatePostInteractions(posts: List<CommunityPost>, currentUserId: Long?): PostInteractionAggregates {
        val postIds = posts.map { it.id }
        if (postIds.isEmpty()) {
            return PostInteractionAggregates(emptyMap(), emptySet(), emptySet())
        }
        val commentCountByPost = commentRepository.countByPostIdIn(postIds)
            .associate { (it[0] as Number).toLong() to (it[1] as Number).toLong() }
        val likedPostIds = currentUserId
            ?.let { likeRepository.findLikedPostIds(postIds, it).toSet() } ?: emptySet()
        val commentedPostIds = currentUserId
            ?.let { commentRepository.findCommentedPostIds(postIds, it).toSet() } ?: emptySet()
        return PostInteractionAggregates(commentCountByPost, likedPostIds, commentedPostIds)
    }
    // Assuming UserRepository needs to be injected or used to fetch currentUser
    fun getPosts(categoryId: Long?, pageable: Pageable, currentUserId: Long?): Page<CommunityPostResponse> {
        val blockedUserIds = currentUserId?.let { userBlockRepository.findBlockedUserIds(it) } ?: emptyList()

        val posts = if (categoryId != null) {
            val category = categoryRepository.findById(categoryId).orElseThrow { NotFoundException("Category not found") }
            if (blockedUserIds.isNotEmpty()) {
                postRepository.findAllByCategoryExcludingUsersAndIsHiddenFalse(category, blockedUserIds, pageable)
            } else {
                postRepository.findAllByCategoryAndIsHiddenFalse(category, pageable)
            }
        } else {
            if (blockedUserIds.isNotEmpty()) {
                postRepository.findAllExcludingUsersAndIsHiddenFalse(blockedUserIds, pageable)
            } else {
                postRepository.findAllByIsHiddenFalse(pageable)
            }
        }

        return toResponsePage(posts, currentUserId)
    }

    /**
     * 트렌딩(인기) 목록 — 최근 글 중 시간감쇠 hot score 상위.
     * 가중치/감쇠/윈도우는 SystemSetting 으로 튜닝(community.trending.*).
     */
    fun getTrendingPosts(categoryId: Long?, pageable: Pageable, currentUserId: Long?): Page<CommunityPostResponse> {
        val blockedUserIds = (currentUserId?.let { userBlockRepository.findBlockedUserIds(it) } ?: emptyList())
            .ifEmpty { listOf(0L) } // sentinel: native NOT IN () 회피 (user id 는 양수)
        val windowDays = systemSettingService.getInt("community.trending.window_days", 14)
        val since = java.time.LocalDateTime.now().minusDays(windowDays.toLong())
        fun w(key: String, default: Double) =
            systemSettingService.getString(key, default.toString()).toDoubleOrNull() ?: default
        // ORDER BY 충돌 방지 위해 unsorted Pageable 사용
        val unsorted = org.springframework.data.domain.PageRequest.of(pageable.pageNumber, pageable.pageSize)
        val posts = postRepository.findTrending(
            categoryId = categoryId,
            blockedUserIds = blockedUserIds,
            since = since,
            wLike = w("community.trending.w_like", 3.0),
            wComment = w("community.trending.w_comment", 5.0),
            wView = w("community.trending.w_view", 1.0),
            gravity = w("community.trending.gravity", 1.5),
            pageable = unsorted,
        )
        return toResponsePage(posts, currentUserId)
    }

    private fun toResponsePage(posts: Page<CommunityPost>, currentUserId: Long?): Page<CommunityPostResponse> {
        // 적대적 리뷰 LOW 후속: isLiked/isCommented/commentCount 는 PERF-004 로 agg 배치 집계로
        // 이전되어 currentUser 엔티티 조회가 isMine 비교에만 남아 있었다(피드 페이지마다 불필요 SELECT).
        // isMine 은 ID 동치 비교라 엔티티 fetch 없이 currentUserId 로 직접 비교 가능 — post.user.id 는
        // FK 로 항상 존재하는 유저를 가리키고 PK 는 재사용되지 않으므로, currentUserId 가 가리키는
        // row 가 존재하지 않으면(탈퇴 등) 어떤 post.user.id 와도 결코 같을 수 없어 기존
        // `currentUser?.id == post.user.id`(존재X 유저 → null → false) 와 동치.
        val previewLength = systemSettingService.getInt("COMMUNITY_POST_PREVIEW_LENGTH", 100)

        warmAuthorAndImages(posts.content) // T1-1.3 N+1 방지: author + image variant batch prefetch
        val agg = aggregatePostInteractions(posts.content, currentUserId) // PERF-004: 좋아요/댓글 배치 집계

        return posts.map { post ->
            val isLiked = agg.isLiked(post.id)
            val isCommented = agg.isCommented(post.id)
            val commentCount = agg.commentCount(post.id)

            val contentPreview = if (post.content.length > previewLength) {
                post.content.substring(0, previewLength) + "..."
            } else {
                post.content
            }

            val img = postImageUrls(post)
            CommunityPostResponse(
                id = post.id,
                authorId = post.user.id,
                authorNickname = post.user.nickname,
                authorProfileUrl = post.user.profileImageUrl.toHttps(),
                authorProfileUrlThumbnail = thumbOf(post.user.profileImageUrl.toHttps()),
                authorProfileUrlViewer = viewerOf(post.user.profileImageUrl.toHttps()),
                categoryId = post.category.id,
                categoryName = post.category.name,
                title = post.title,
                content = contentPreview,
                visibility = post.visibility,
                postType = post.postType,
                viewCount = post.viewCount,
                likeCount = post.likeCount,
                commentCount = commentCount,
                createdAt = post.createdAt,
                isLiked = isLiked,
                isCommentedByMe = isCommented,
                imageUrls = img.originals,
                imageUrlsThumbnail = img.thumbnails,
                imageUrlsMedium = img.mediums,
                imageUrlsViewer = img.viewers,
                imageUrlsThumbnailWebp = img.thumbnailWebps,
                isMine = currentUserId != null && currentUserId == post.user.id
            )
        }
    }

    @Cacheable("community:categories")
    fun getCategories(): List<CommunityCategoryResponse> {
        log.debug("getCategories called (Cache Miss if seeing this)")
        return getCachedCategoryEntities().map { category ->
            val count = postRepository.countByCategoryAndIsHiddenFalse(category)
            log.debug("Category {} count: {}", category.name, count)
            CommunityCategoryResponse(
                id = category.id,
                name = category.name,
                code = category.code,
                postCount = count
            )
        }
    }

    @Cacheable("community:categories:entities")
    fun getCachedCategoryEntities(): List<com.goldpet.domain.community.entity.CommunityCategory> {
        return categoryRepository.findAll()
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = ["community:categories"], allEntries = true)
    fun createPost(userId: Long, request: CommunityPostCreateRequest): CommunityPostResponse {
        log.debug("createPost called for user {}, category {}", userId, request.categoryId)
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val category = categoryRepository.findById(request.categoryId).orElseThrow { NotFoundException("Category not found") }

        val post = CommunityPost(
            user = user,
            category = category,
            title = request.title,
            content = request.content,
            visibility = request.visibility,
            postType = request.postType
        )

        if (request.imageUrls.isNotEmpty()) {
            val files = fileRepository.findAllByUrlIn(request.imageUrls)
            val fileMap = files.associateBy { it.url }
            
            request.imageUrls.forEachIndexed { index, url ->
                fileMap[url]?.let { file ->
                    post.images.add(CommunityPostImage(post = post, file = file, sortOrder = index))
                }
            }
        }
        
        val savedPost = postRepository.save(post)

        try {
            badgeAwardService.checkAndAwardBadges(userId, BadgeConditionType.COMMUNITY_POST)
        } catch (e: Exception) {
            log.warn("Badge check failed for userId={}", userId, e)
        }

        val img = postImageUrls(savedPost)
        return CommunityPostResponse(
            id = savedPost.id,
            authorId = user.id,
            authorNickname = user.nickname,
            authorProfileUrl = user.profileImageUrl.toHttps(),
            authorProfileUrlThumbnail = thumbOf(user.profileImageUrl.toHttps()),
            authorProfileUrlViewer = viewerOf(user.profileImageUrl.toHttps()),
            categoryId = category.id,
            categoryName = category.name,
            title = savedPost.title,
            content = savedPost.content,
            visibility = savedPost.visibility,
            postType = savedPost.postType,
            viewCount = savedPost.viewCount,
            likeCount = savedPost.likeCount,
            commentCount = 0,
            createdAt = savedPost.createdAt,
            imageUrls = img.originals,
            imageUrlsThumbnail = img.thumbnails,
            imageUrlsMedium = img.mediums,
            imageUrlsViewer = img.viewers,
            imageUrlsThumbnailWebp = img.thumbnailWebps,
            isMine = true
        )
    }

    @Transactional
    fun toggleLike(userId: Long, postId: Long): Boolean {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val post = postRepository.findById(postId).orElseThrow { NotFoundException("Post not found") }
        if (post.isHidden) throw NotFoundException("Post not found")

        val existingLike = likeRepository.findByPostAndUser(post, user)
        return if (existingLike != null) {
            likeRepository.delete(existingLike)
            post.likeCount = (post.likeCount - 1).coerceAtLeast(0)
            false
        } else {
            likeRepository.save(CommunityPostLike(post = post, user = user))
            post.likeCount += 1
            true
        }
    }

    @Transactional
    fun createComment(userId: Long, postId: Long, request: CommunityCommentCreateRequest): CommunityCommentResponse {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val post = postRepository.findById(postId).orElseThrow { NotFoundException("Post not found") }
        if (post.isHidden) throw NotFoundException("Post not found")

        val parentComment = request.parentCommentId?.let {
            commentRepository.findById(it).orElseThrow { NotFoundException("Parent comment not found") }
        }

        val comment = CommunityComment(
            post = post,
            user = user,
            parentComment = parentComment,
            content = request.content
        )
        
        val savedComment = commentRepository.save(comment)

        try {
            badgeAwardService.checkAndAwardBadges(userId, BadgeConditionType.COMMUNITY_COMMENT)
        } catch (e: Exception) {
            log.warn("Badge check failed for userId={}", userId, e)
        }

        return CommunityCommentResponse(
            id = savedComment.id,
            postId = post.id,
            authorId = user.id,
            authorNickname = user.nickname,
            authorProfileUrl = user.profileImageUrl.toHttps(),
            authorProfileUrlThumbnail = thumbOf(user.profileImageUrl.toHttps()),
            authorProfileUrlViewer = viewerOf(user.profileImageUrl.toHttps()),
            content = savedComment.content,
            createdAt = savedComment.createdAt,
            parentCommentId = parentComment?.id,
            isMine = true
        )
    }

    @Transactional
    fun toggleCommentLike(userId: Long, commentId: Long): Boolean {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val comment = commentRepository.findById(commentId).orElseThrow { NotFoundException("Comment not found") }
        if (comment.isHidden) throw NotFoundException("Comment not found")

        val existingLike = commentLikeRepository.findByCommentAndUser(comment, user)
        return if (existingLike != null) {
            commentLikeRepository.delete(existingLike)
            comment.likeCount = (comment.likeCount - 1).coerceAtLeast(0)
            false
        } else {
            commentLikeRepository.save(com.goldpet.domain.community.entity.CommunityCommentLike(comment = comment, user = user))
            comment.likeCount += 1
            true
        }
    }

    @Transactional
    fun getPostDetail(postId: Long, currentUserId: Long?): CommunityPostResponse {
        val post = postRepository.findById(postId).orElseThrow { NotFoundException("Post not found") }
        if (post.isHidden) throw NotFoundException("Post not found")

        // Increment view count
        post.viewCount += 1
        postRepository.save(post)

        val isLiked = currentUserId?.let { userId ->
            val user = userRepository.findById(userId).orElse(null)
            user?.let { likeRepository.existsByPostAndUser(post, it) } ?: false
        } ?: false

        val isCommented = currentUserId?.let { userId ->
            commentRepository.existsByPostIdAndUserId(postId, userId)
        } ?: false

        val commentCount = commentRepository.countByPostId(postId)

        // Fetch all non-hidden comments, filter blocked users, and sort recursively
        val blockedIds = currentUserId?.let { userBlockRepository.findBlockedUserIds(it) }?.toSet() ?: emptySet()
        val allComments = commentRepository.findByPostIdAndIsHiddenFalseOrderByCreatedAtAsc(postId)
            .filter { it.user.id !in blockedIds }

        // T1-1.3 batch prefetch: post author + post images + comment authors 를 한 번의 IN 쿼리로.
        warmAuthorAndImages(listOf(post))
        warmCommentAuthors(allComments)

        val commentMap = allComments.groupBy { it.parentComment?.id }
        val sortedComments = mutableListOf<CommunityCommentResponse>()

        fun traverse(parentId: Long?, depth: Int) {
            val children = commentMap[parentId] ?: return
            children.forEach { comment ->
                val isCommentLiked = currentUserId?.let { userId ->
                    val user = userRepository.findById(userId).orElse(null)
                    user?.let { commentLikeRepository.existsByCommentAndUser(comment, it) } ?: false
                } ?: false

                val isReplied = currentUserId?.let { userId ->
                    commentRepository.existsByParentCommentIdAndUserId(comment.id, userId)
                } ?: false
                
                // Calculate logic depth. If parent is null, depth is 1.
                // The recursion passes depth + 1. 
                // We clamp visual depth to 3 if needed, but data depth should be accurate?
                // User said "can do 3 depth".
                
                sortedComments.add(
                    CommunityCommentResponse(
                        id = comment.id,
                        postId = post.id,
                        authorId = comment.user.id,
                        authorNickname = comment.user.nickname,
                        authorProfileUrl = comment.user.profileImageUrl.toHttps(),
                        authorProfileUrlThumbnail = thumbOf(comment.user.profileImageUrl.toHttps()),
                        authorProfileUrlViewer = viewerOf(comment.user.profileImageUrl.toHttps()),
                        content = comment.content,
                        createdAt = comment.createdAt,
                        parentCommentId = comment.parentComment?.id,
                        likeCount = comment.likeCount,
                        isLiked = isCommentLiked,
                        isRepliedByMe = isReplied,
                        depth = depth,
                        replyCount = commentMap[comment.id]?.size ?: 0,
                        isMine = currentUserId == comment.user.id
                    )
                )
                
                // Recurse for children
                traverse(comment.id, depth + 1)
            }
        }

        // Start traversal from root comments (parentId = null)
        traverse(null, 1)        
        
        val comments = sortedComments

        val img = postImageUrls(post)
        return CommunityPostResponse(
            id = post.id,
            authorId = post.user.id,
            authorNickname = post.user.nickname,
            authorProfileUrl = post.user.profileImageUrl.toHttps(),
                authorProfileUrlThumbnail = thumbOf(post.user.profileImageUrl.toHttps()),
                authorProfileUrlViewer = viewerOf(post.user.profileImageUrl.toHttps()),
            categoryId = post.category.id,
            categoryName = post.category.name,
            title = post.title,
            content = post.content,
            visibility = post.visibility,
            postType = post.postType,
            viewCount = post.viewCount,
            likeCount = post.likeCount,
            commentCount = commentCount,
            createdAt = post.createdAt,
            isLiked = isLiked,
            isCommentedByMe = isCommented,
            imageUrls = img.originals,
            imageUrlsThumbnail = img.thumbnails,
            imageUrlsMedium = img.mediums,
            imageUrlsViewer = img.viewers,
            imageUrlsThumbnailWebp = img.thumbnailWebps,
            comments = comments,
            isMine = currentUserId == post.user.id
        )
    }

    fun getComments(postId: Long, pageable: Pageable): Page<CommunityCommentResponse> {
        val post = postRepository.findById(postId).orElseThrow { NotFoundException("Post not found") }
        if (post.isHidden) throw NotFoundException("Post not found")
        val comments = commentRepository.findAllByPostAndIsHiddenFalse(post, pageable)
        warmCommentAuthors(comments.content) // T1-1.3 batch prefetch
        return comments.map { comment ->
            CommunityCommentResponse(
                id = comment.id,
                postId = post.id,
                authorId = comment.user.id,
                authorNickname = comment.user.nickname,
                authorProfileUrl = comment.user.profileImageUrl.toHttps(),
                        authorProfileUrlThumbnail = thumbOf(comment.user.profileImageUrl.toHttps()),
                        authorProfileUrlViewer = viewerOf(comment.user.profileImageUrl.toHttps()),
                content = comment.content,
                createdAt = comment.createdAt,
                parentCommentId = comment.parentComment?.id
            )
        }
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(
        value = ["community:categories"],
        allEntries = true,
        condition = "#request.categoryId != null"
    )
    fun updatePost(postId: Long, userId: Long, request: CommunityPostUpdateRequest): CommunityPostResponse {
        val post = postRepository.findById(postId).orElseThrow { NotFoundException("Post not found") }
        if (post.isHidden) throw NotFoundException("Post not found")

        if (post.user.id != userId) {
            throw ForbiddenException("Not authorized to update this post")
        }

        request.categoryId?.let { newCategoryId ->
            if (newCategoryId != post.category.id) {
                val newCategory = categoryRepository.findById(newCategoryId)
                    .orElseThrow { NotFoundException("Category not found: $newCategoryId") }
                post.category = newCategory
            }
        }
        request.title?.let { post.title = it }
        request.content?.let { post.content = it }
        request.visibility?.let { post.visibility = CommunityPost.Visibility.valueOf(it) }
        request.imageUrls?.let { urls ->
            post.images.clear()
            if (urls.isNotEmpty()) {
                val files = fileRepository.findAllByUrlIn(urls)
                val fileMap = files.associateBy { it.url }

                urls.forEachIndexed { index, url ->
                    fileMap[url]?.let { file ->
                        post.images.add(CommunityPostImage(post = post, file = file, sortOrder = index))
                    }
                }
            }
        }

        val savedPost = postRepository.save(post)

        return CommunityPostResponse(
            id = savedPost.id,
            authorId = savedPost.user.id,
            authorNickname = savedPost.user.nickname,
            authorProfileUrl = savedPost.user.profileImageUrl.toHttps(),
            authorProfileUrlThumbnail = thumbOf(savedPost.user.profileImageUrl.toHttps()),
            authorProfileUrlViewer = viewerOf(savedPost.user.profileImageUrl.toHttps()),
            categoryId = savedPost.category.id,
            categoryName = savedPost.category.name,
            title = savedPost.title,
            content = savedPost.content,
            visibility = savedPost.visibility,
            postType = savedPost.postType,
            viewCount = savedPost.viewCount,
            likeCount = savedPost.likeCount,
            commentCount = 0,
            createdAt = savedPost.createdAt,
            isMine = true
        )
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = ["community:categories"], allEntries = true)
    fun deletePost(postId: Long, userId: Long) {
        val post = postRepository.findById(postId).orElseThrow { NotFoundException("Post not found") }
        if (post.isHidden) throw NotFoundException("Post not found")

        if (post.user.id != userId) {
            throw ForbiddenException("Not authorized to delete this post")
        }

        postRepository.delete(post)
    }

    fun getMyPosts(userId: Long, pageable: Pageable): Page<CommunityPostResponse> {
        val posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        val previewLength = systemSettingService.getInt("COMMUNITY_POST_PREVIEW_LENGTH", 100)

        warmAuthorAndImages(posts.content) // T1-1.3 batch prefetch
        // PERF-004: 댓글수 배치 집계 (게시글마다 countByPostId 대신 postIds IN 1회).
        val postIds = posts.content.map { it.id }
        val commentCountByPost = if (postIds.isEmpty()) emptyMap() else
            commentRepository.countByPostIdIn(postIds)
                .associate { (it[0] as Number).toLong() to (it[1] as Number).toLong() }

        return posts.map { post ->
            val commentCount = commentCountByPost[post.id] ?: 0L
            val contentPreview = if (post.content.length > previewLength) {
                post.content.substring(0, previewLength) + "..."
            } else {
                post.content
            }

            val img = postImageUrls(post)
            CommunityPostResponse(
                id = post.id,
                authorId = post.user.id,
                authorNickname = post.user.nickname,
                authorProfileUrl = post.user.profileImageUrl.toHttps(),
                authorProfileUrlThumbnail = thumbOf(post.user.profileImageUrl.toHttps()),
                authorProfileUrlViewer = viewerOf(post.user.profileImageUrl.toHttps()),
                categoryId = post.category.id,
                categoryName = post.category.name,
                title = post.title,
                content = contentPreview,
                visibility = post.visibility,
                postType = post.postType,
                viewCount = post.viewCount,
                likeCount = post.likeCount,
                commentCount = commentCount,
                createdAt = post.createdAt,
                imageUrls = img.originals,
                imageUrlsThumbnail = img.thumbnails,
                imageUrlsMedium = img.mediums,
                imageUrlsViewer = img.viewers,
                imageUrlsThumbnailWebp = img.thumbnailWebps,
                isMine = true
            )
        }
    }

    fun getMyComments(userId: Long, pageable: Pageable): Page<CommunityCommentResponse> {
        val comments = commentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        warmCommentAuthors(comments.content) // T1-1.3 batch prefetch

        return comments.map { comment ->
            CommunityCommentResponse(
                id = comment.id,
                postId = comment.post.id,
                authorId = comment.user.id,
                authorNickname = comment.user.nickname,
                authorProfileUrl = comment.user.profileImageUrl.toHttps(),
                        authorProfileUrlThumbnail = thumbOf(comment.user.profileImageUrl.toHttps()),
                        authorProfileUrlViewer = viewerOf(comment.user.profileImageUrl.toHttps()),
                content = comment.content,
                createdAt = comment.createdAt,
                parentCommentId = comment.parentComment?.id,
                likeCount = comment.likeCount,
                isMine = true,
                postTitle = comment.post.title
            )
        }
    }

    fun searchPosts(keyword: String, pageable: Pageable, currentUserId: Long?): Page<CommunityPostResponse> {
        val blockedUserIds = currentUserId?.let { userBlockRepository.findBlockedUserIds(it) } ?: emptyList()
        val posts = if (blockedUserIds.isNotEmpty()) {
            postRepository.searchByKeywordExcludingUsersAndIsHiddenFalse(keyword, blockedUserIds, pageable)
        } else {
            postRepository.searchByKeywordAndIsHiddenFalse(keyword, pageable)
        }
        val currentUser = currentUserId?.let { userRepository.findById(it).orElse(null) }

        warmAuthorAndImages(posts.content) // T1-1.3 batch prefetch
        // PERF-004: 내 좋아요여부 배치 집계 (게시글마다 existsByPostAndUser 대신 postIds IN 1회).
        val searchPostIds = posts.content.map { it.id }
        val likedPostIds = if (currentUser == null || searchPostIds.isEmpty()) emptySet()
            else likeRepository.findLikedPostIds(searchPostIds, currentUser.id).toSet()

        return posts.map { post ->
            val isLiked = post.id in likedPostIds
            CommunityPostResponse(
                id = post.id,
                authorId = post.user.id,
                authorNickname = post.user.nickname,
                authorProfileUrl = post.user.profileImageUrl.toHttps(),
                authorProfileUrlThumbnail = thumbOf(post.user.profileImageUrl.toHttps()),
                authorProfileUrlViewer = viewerOf(post.user.profileImageUrl.toHttps()),
                categoryId = post.category.id,
                categoryName = post.category.name,
                title = post.title,
                content = post.content,
                visibility = post.visibility,
                postType = post.postType,
                viewCount = post.viewCount,
                likeCount = post.likeCount,
                commentCount = 0,
                createdAt = post.createdAt,
                isLiked = isLiked,
                isMine = currentUser?.id == post.user.id
            )
        }
    }

    @Transactional
    fun updateComment(commentId: Long, userId: Long, content: String): CommunityCommentResponse {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException("Comment not found") }

        if (comment.user.id != userId) {
            throw AccessDeniedException("Not authorized to update this comment")
        }

        comment.content = content
        val saved = commentRepository.save(comment)

        return CommunityCommentResponse(
            id = saved.id,
            postId = saved.post.id,
            authorId = saved.user.id,
            authorNickname = saved.user.nickname,
            authorProfileUrl = saved.user.profileImageUrl.toHttps(),
            authorProfileUrlThumbnail = thumbOf(saved.user.profileImageUrl.toHttps()),
            authorProfileUrlViewer = viewerOf(saved.user.profileImageUrl.toHttps()),
            content = saved.content,
            createdAt = saved.createdAt,
            parentCommentId = saved.parentComment?.id,
            likeCount = saved.likeCount,
            isMine = true
        )
    }

    @Transactional
    fun deleteComment(commentId: Long, userId: Long) {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException("Comment not found") }

        if (comment.user.id != userId) {
            throw AccessDeniedException("Not authorized to delete this comment")
        }

        // Step 1: Nullify adopted_comment_id FK if this comment is adopted on any post
        val adoptingPost = postRepository.findByAdoptedComment(comment)
        if (adoptingPost != null) {
            adoptingPost.adoptedComment = null
            postRepository.save(adoptingPost)
        }

        // Step 2: Recursively collect all descendant comments (depth-first)
        val allDescendants = mutableListOf<CommunityComment>()
        fun collectDescendants(parent: CommunityComment) {
            val children = commentRepository.findAllByParentComment(parent)
            for (child in children) {
                collectDescendants(child) // recurse first (depth-first)
                allDescendants.add(child)
            }
        }
        collectDescendants(comment)

        // Step 3: Delete likes on all descendant comments (batch)
        if (allDescendants.isNotEmpty()) {
            commentLikeRepository.deleteAllByCommentIn(allDescendants)
        }

        // Step 4: Delete likes on the target comment itself
        commentLikeRepository.deleteAllByComment(comment)

        // Step 5: Delete descendant comments (they are in leaf-first order from depth-first traversal)
        for (descendant in allDescendants) {
            // Also nullify adopted_comment_id for descendants if any
            val descendantAdoptingPost = postRepository.findByAdoptedComment(descendant)
            if (descendantAdoptingPost != null) {
                descendantAdoptingPost.adoptedComment = null
                postRepository.save(descendantAdoptingPost)
            }
            commentRepository.delete(descendant)
        }

        // Step 6: Delete the target comment
        commentRepository.delete(comment)
    }
}
