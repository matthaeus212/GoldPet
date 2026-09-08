package com.goldpet.domain.community.entity

import com.goldpet.domain.common.entity.BaseTimeEntity
import com.goldpet.domain.user.entity.User
import jakarta.persistence.*

@Entity
@Table(name = "community_posts")
class CommunityPost(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: CommunityCategory,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var visibility: Visibility = Visibility.PUBLIC,

    @Column(nullable = false)
    var viewCount: Int = 0,

    @Column(nullable = false)
    var likeCount: Int = 0,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var postType: PostType = PostType.GENERAL,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adopted_comment_id")
    var adoptedComment: CommunityComment? = null,

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    var images: MutableList<CommunityPostImage> = mutableListOf(),

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    var comments: MutableList<CommunityComment> = mutableListOf(),

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    var likes: MutableList<CommunityPostLike> = mutableListOf(),

    @Column(nullable = false)
    var isHidden: Boolean = false

) : BaseTimeEntity() {
    enum class Visibility {
        PUBLIC, FRIENDS
    }

    enum class PostType {
        GENERAL, QUESTION, INFO
    }
}
