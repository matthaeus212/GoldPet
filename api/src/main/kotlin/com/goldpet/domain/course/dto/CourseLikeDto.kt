package com.goldpet.domain.course.dto

data class CourseLikeStatusResponse(
    val courseId: Long,
    val liked: Boolean,
    val likeCount: Int
)
