package com.goldpet.domain.community.entity

import com.goldpet.domain.common.entity.FileAttachment
import jakarta.persistence.*

@Entity
@Table(name = "community_post_images")
class CommunityPostImage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: CommunityPost,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    val file: FileAttachment,

    @Column(nullable = false, name = "sort_order")
    val sortOrder: Int
)
