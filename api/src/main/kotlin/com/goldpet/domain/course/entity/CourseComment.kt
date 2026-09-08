package com.goldpet.domain.course.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*

@Entity
@Table(name = "course_comments")
class CourseComment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: WalkCourse,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    val parentComment: CourseComment? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    var rating: Int? = null,

    @Column(nullable = false)
    var likeCount: Int = 0,

    @Column(nullable = false)
    var isHidden: Boolean = false

) : BaseTimeEntity()
