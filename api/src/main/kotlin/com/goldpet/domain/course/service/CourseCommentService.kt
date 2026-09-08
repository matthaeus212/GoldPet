package com.goldpet.domain.course.service

import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.exception.ForbiddenException
import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.course.dto.CourseCommentResponse
import com.goldpet.domain.course.dto.CreateCourseCommentRequest
import com.goldpet.domain.course.dto.UpdateCourseCommentRequest
import com.goldpet.domain.course.entity.CourseComment
import com.goldpet.domain.course.entity.CourseCommentLike
import com.goldpet.domain.course.repository.CourseCommentLikeRepository
import com.goldpet.domain.course.repository.CourseCommentRepository
import com.goldpet.domain.course.repository.WalkCourseRepository
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CourseCommentService(
    private val walkCourseRepository: WalkCourseRepository,
    private val commentRepository: CourseCommentRepository,
    private val commentLikeRepository: CourseCommentLikeRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun createComment(courseId: Long, userId: Long, request: CreateCourseCommentRequest): CourseCommentResponse {
        if (request.parentCommentId != null && request.rating != null) {
            throw BadRequestException("대댓글에는 평점을 남길 수 없습니다")
        }

        val course = walkCourseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Course not found") }
        if (!course.isPublished) throw NotFoundException("Course not found")

        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }
        val parentComment = request.parentCommentId?.let {
            commentRepository.findById(it).orElseThrow { NotFoundException("Parent comment not found") }
        }

        val comment = CourseComment(
            course = course,
            user = user,
            parentComment = parentComment,
            content = request.content,
            rating = request.rating
        )
        val savedComment = commentRepository.save(comment)

        if (request.rating != null) {
            walkCourseRepository.addRating(courseId, request.rating.toDouble())
        }
        walkCourseRepository.updateCommentCount(courseId, 1)

        return CourseCommentResponse.from(savedComment)
    }

    @Transactional
    fun updateComment(commentId: Long, userId: Long, request: UpdateCourseCommentRequest): CourseCommentResponse {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException("Comment not found") }
        if (comment.user.id != userId) throw ForbiddenException("Not authorized to update this comment")

        val oldRating = comment.rating
        val newRating = request.rating

        // Adjust rating if changed
        if (oldRating != null && newRating != null && oldRating != newRating) {
            val course = comment.course
            val oldSum = course.rating * course.ratingCount
            val newSum = oldSum - oldRating + newRating
            course.rating = if (course.ratingCount > 0) newSum / course.ratingCount else 0.0
            walkCourseRepository.save(course)
        }

        comment.content = request.content
        if (newRating != null) comment.rating = newRating
        val saved = commentRepository.save(comment)
        return CourseCommentResponse.from(saved)
    }

    @Transactional
    fun deleteComment(commentId: Long, userId: Long) {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException("Comment not found") }
        if (comment.user.id != userId) throw ForbiddenException("Not authorized to delete this comment")

        val courseId = comment.course.id

        // Adjust rating before deletion
        if (comment.rating != null) {
            val course = comment.course
            if (course.ratingCount > 1) {
                val oldSum = course.rating * course.ratingCount
                val newRatingCount = course.ratingCount - 1
                course.rating = (oldSum - comment.rating!!) / newRatingCount
                course.ratingCount = newRatingCount
            } else {
                course.rating = 0.0
                course.ratingCount = 0
            }
            walkCourseRepository.save(course)
        }

        // Delete child comments first (course_comments.parent_comment_id has no ON DELETE CASCADE)
        val children = commentRepository.findAllByCourseId(courseId).filter { it.parentComment?.id == commentId }
        commentRepository.deleteAll(children)

        commentRepository.delete(comment)
        walkCourseRepository.updateCommentCount(courseId, -1)
    }

    fun getComments(courseId: Long, pageable: Pageable): Page<CourseCommentResponse> {
        val course = walkCourseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Course not found") }
        if (!course.isPublished) throw NotFoundException("Course not found")
        return commentRepository.findByCourseIdOrderByCreatedAtDesc(courseId, pageable)
            .map { CourseCommentResponse.from(it) }
    }

    @Transactional
    fun toggleCommentLike(commentId: Long, userId: Long): Boolean {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException("Comment not found") }
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }

        val existingLike = commentLikeRepository.findByCommentIdAndUserId(commentId, userId)
        return if (existingLike != null) {
            commentLikeRepository.delete(existingLike)
            comment.likeCount = (comment.likeCount - 1).coerceAtLeast(0)
            commentRepository.save(comment)
            false
        } else {
            commentLikeRepository.save(CourseCommentLike(comment = comment, user = user))
            comment.likeCount += 1
            commentRepository.save(comment)
            true
        }
    }
}
