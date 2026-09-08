package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.*
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.community.entity.CommunityPost
import com.goldpet.domain.community.repository.CommunityCommentRepository
import com.goldpet.domain.community.repository.CommunityPostLikeRepository
import com.goldpet.domain.community.repository.CommunityPostRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
class AdminCommunityService(
    private val postRepository: CommunityPostRepository,
    private val commentRepository: CommunityCommentRepository,
    private val likeRepository: CommunityPostLikeRepository
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun getPosts(pageable: Pageable, search: String?, categoryId: Long?, includeHidden: Boolean): Page<PostListItemResponse> {
        val postsPage = when {
            !search.isNullOrBlank() -> postRepository.findByTitleContainingIgnoreCase(search, pageable)
            else -> postRepository.findAll(pageable)
        }

        return postsPage.map { post -> toPostListItem(post) }
    }

    fun getPostDetail(postId: Long): PostDetailAdminResponse {
        val post = postRepository.findById(postId)
            .orElseThrow { NotFoundException("Post not found: $postId") }

        val comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId)
        val likeCount = likeRepository.countByPostId(postId)

        return PostDetailAdminResponse(
            id = post.id,
            title = post.title,
            content = post.content,
            categoryId = post.category.id,
            categoryName = post.category.name,
            userId = post.user.id,
            userNickname = post.user.nickname ?: "",
            userProfileImage = post.user.profileImageUrl,
            imageUrls = post.images.sortedBy { it.sortOrder }.map { it.file.url },
            viewCount = post.viewCount,
            likeCount = likeCount.toInt(),
            commentCount = comments.size,
            isHidden = post.isHidden,
            createdAt = post.createdAt.format(formatter),
            updatedAt = post.updatedAt.format(formatter),
            comments = comments.map { comment ->
                CommentAdminResponse(
                    id = comment.id,
                    content = comment.content,
                    userId = comment.user.id,
                    userNickname = comment.user.nickname ?: "",
                    isHidden = comment.isHidden,
                    createdAt = comment.createdAt.format(formatter)
                )
            }
        )
    }

    @Transactional
    fun updatePost(postId: Long, request: AdminPostUpdateRequest): PostDetailAdminResponse {
        val post = postRepository.findById(postId)
            .orElseThrow { NotFoundException("Post not found: $postId") }
        request.title?.let { post.title = it }
        request.content?.let { post.content = it }
        postRepository.save(post)
        return getPostDetail(postId)
    }

    @Transactional
    fun updateComment(commentId: Long, request: AdminCommentUpdateRequest): CommentAdminResponse {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException("Comment not found: $commentId") }
        comment.content = request.content
        val saved = commentRepository.save(comment)
        return CommentAdminResponse(
            id = saved.id,
            content = saved.content,
            userId = saved.user.id,
            userNickname = saved.user.nickname ?: "",
            isHidden = saved.isHidden,
            createdAt = saved.createdAt.format(formatter)
        )
    }

    @Transactional
    fun hidePost(postId: Long, reason: String?) {
        val post = postRepository.findById(postId)
            .orElseThrow { NotFoundException("Post not found: $postId") }
        post.isHidden = true
        postRepository.save(post)
    }

    @Transactional
    fun unhidePost(postId: Long) {
        val post = postRepository.findById(postId)
            .orElseThrow { NotFoundException("Post not found: $postId") }
        post.isHidden = false
        postRepository.save(post)
    }

    @Transactional
    fun deletePost(postId: Long) {
        postRepository.deleteById(postId)
    }

    @Transactional
    fun hideComment(commentId: Long, reason: String?) {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException("Comment not found: $commentId") }
        comment.isHidden = true
        commentRepository.save(comment)
    }

    @Transactional
    fun deleteComment(commentId: Long) {
        commentRepository.deleteById(commentId)
    }

    private fun toPostListItem(post: CommunityPost): PostListItemResponse {
        val likeCount = likeRepository.countByPostId(post.id)
        val commentCount = commentRepository.countByPostId(post.id)

        return PostListItemResponse(
            id = post.id,
            title = post.title,
            content = if (post.content.length > 100) post.content.take(100) + "..." else post.content,
            categoryName = post.category.name,
            userNickname = post.user.nickname ?: "",
            userId = post.user.id,
            viewCount = post.viewCount,
            likeCount = likeCount.toInt(),
            commentCount = commentCount.toInt(),
            isHidden = post.isHidden,
            createdAt = post.createdAt.format(formatter)
        )
    }
}
