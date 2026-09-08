package com.goldpet.domain.course.repository

import com.goldpet.domain.course.entity.CourseComment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CourseCommentRepository : JpaRepository<CourseComment, Long> {
    fun findByCourseIdOrderByCreatedAtDesc(courseId: Long, pageable: Pageable): Page<CourseComment>
    fun countByCourseId(courseId: Long): Long
    fun findAllByCourseId(courseId: Long): List<CourseComment>
    fun deleteAllByCourseId(courseId: Long)
}
