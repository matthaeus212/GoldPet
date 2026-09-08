package com.goldpet.domain.course.repository

import com.goldpet.domain.course.entity.CourseCommentLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CourseCommentLikeRepository : JpaRepository<CourseCommentLike, Long> {
    fun findByCommentIdAndUserId(commentId: Long, userId: Long): CourseCommentLike?
    fun existsByCommentIdAndUserId(commentId: Long, userId: Long): Boolean

    @Modifying
    @Query("DELETE FROM CourseCommentLike ccl WHERE ccl.comment.id = :commentId AND ccl.user.id = :userId")
    fun deleteByCommentIdAndUserId(@Param("commentId") commentId: Long, @Param("userId") userId: Long)

    @Modifying
    @Query("DELETE FROM CourseCommentLike ccl WHERE ccl.comment.course.id = :courseId")
    fun deleteAllByCourseId(@Param("courseId") courseId: Long)

    @Modifying
    @Query("DELETE FROM CourseCommentLike ccl WHERE ccl.comment.id = :commentId")
    fun deleteAllByCommentId(@Param("commentId") commentId: Long)
}
