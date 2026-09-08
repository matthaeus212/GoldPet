package com.goldpet.domain.course.repository

import com.goldpet.domain.course.entity.CourseLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CourseLikeRepository : JpaRepository<CourseLike, Long> {
    fun findByCourseIdAndUserId(courseId: Long, userId: Long): CourseLike?
    fun existsByCourseIdAndUserId(courseId: Long, userId: Long): Boolean

    @Modifying
    @Query("DELETE FROM CourseLike cl WHERE cl.course.id = :courseId AND cl.user.id = :userId")
    fun deleteByCourseIdAndUserId(@Param("courseId") courseId: Long, @Param("userId") userId: Long)

    fun deleteAllByCourseId(courseId: Long)
}
