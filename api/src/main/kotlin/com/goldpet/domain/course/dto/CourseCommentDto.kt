package com.goldpet.domain.course.dto

import com.goldpet.domain.course.entity.CourseComment
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateCourseCommentRequest(
    @field:NotBlank(message = "댓글 내용을 입력해주세요")
    @field:Size(max = 1000, message = "댓글은 1000자 이내로 입력해주세요")
    val content: String,

    @field:Min(1) @field:Max(5)
    val rating: Int? = null,

    val parentCommentId: Long? = null
)

data class UpdateCourseCommentRequest(
    @field:NotBlank(message = "댓글 내용을 입력해주세요")
    @field:Size(max = 1000, message = "댓글은 1000자 이내로 입력해주세요")
    val content: String,

    @field:Min(1) @field:Max(5)
    val rating: Int? = null
)

data class CourseCommentResponse(
    val id: Long,
    val courseId: Long,
    val userId: Long,
    val userNickname: String,
    val userProfileImageUrl: String?,
    val parentCommentId: Long?,
    val content: String,
    val rating: Int?,
    val likeCount: Int,
    val isHidden: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(comment: CourseComment) = CourseCommentResponse(
            id = comment.id,
            courseId = comment.course.id,
            userId = comment.user.id,
            userNickname = comment.user.nickname ?: "",
            userProfileImageUrl = comment.user.profileImageUrl,
            parentCommentId = comment.parentComment?.id,
            content = comment.content,
            rating = comment.rating,
            likeCount = comment.likeCount,
            isHidden = comment.isHidden,
            createdAt = comment.createdAt,
            updatedAt = comment.updatedAt
        )
    }
}
