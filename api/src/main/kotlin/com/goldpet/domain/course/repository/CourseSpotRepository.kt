package com.goldpet.domain.course.repository

import com.goldpet.domain.course.entity.CourseSpot
import org.springframework.data.jpa.repository.JpaRepository

interface CourseSpotRepository : JpaRepository<CourseSpot, Long> {
    fun findByCourseIdOrderByOrderIndex(courseId: Long): List<CourseSpot>
    fun deleteByCourseId(courseId: Long)
}
