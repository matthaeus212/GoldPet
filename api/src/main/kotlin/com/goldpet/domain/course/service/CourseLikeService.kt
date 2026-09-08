package com.goldpet.domain.course.service

import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.course.dto.CourseLikeStatusResponse
import com.goldpet.domain.course.entity.CourseLike
import com.goldpet.domain.course.repository.CourseLikeRepository
import com.goldpet.domain.course.repository.WalkCourseRepository
import com.goldpet.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CourseLikeService(
    private val walkCourseRepository: WalkCourseRepository,
    private val courseLikeRepository: CourseLikeRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun toggleLike(courseId: Long, userId: Long): CourseLikeStatusResponse {
        val course = walkCourseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Course not found") }
        if (!course.isPublished) throw NotFoundException("Course not found")
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("User not found") }

        val existingLike = courseLikeRepository.findByCourseIdAndUserId(courseId, userId)
        val liked: Boolean
        if (existingLike != null) {
            courseLikeRepository.delete(existingLike)
            walkCourseRepository.updateLikeCount(courseId, -1)
            liked = false
        } else {
            courseLikeRepository.save(CourseLike(course = course, user = user))
            walkCourseRepository.updateLikeCount(courseId, 1)
            liked = true
        }

        val updatedCourse = walkCourseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Course not found") }
        return CourseLikeStatusResponse(
            courseId = courseId,
            liked = liked,
            likeCount = updatedCourse.likeCount
        )
    }

    fun getLikeStatus(courseId: Long, userId: Long): CourseLikeStatusResponse {
        val course = walkCourseRepository.findById(courseId)
            .orElseThrow { NotFoundException("Course not found") }
        val liked = courseLikeRepository.existsByCourseIdAndUserId(courseId, userId)
        return CourseLikeStatusResponse(
            courseId = courseId,
            liked = liked,
            likeCount = course.likeCount
        )
    }
}
