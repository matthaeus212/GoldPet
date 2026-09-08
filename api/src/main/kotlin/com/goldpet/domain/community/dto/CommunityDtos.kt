package com.goldpet.domain.community.dto

import com.goldpet.domain.community.entity.CommunityPost
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CommunityPostResponse(
    val id: Long,
    val authorId: Long,
    val authorNickname: String?,
    val authorProfileUrl: String?,
    /** T1-1.3: author profile thumbnail (200px). */
    val authorProfileUrlThumbnail: String? = null,
    /** T1-1.3: author profile viewer (1600px). */
    val authorProfileUrlViewer: String? = null,
    val categoryId: Long,
    val categoryName: String,
    val title: String,
    val content: String,
    val visibility: CommunityPost.Visibility,
    val postType: CommunityPost.PostType,
    val viewCount: Int,
    val likeCount: Int,
    val commentCount: Long = 0,
    val createdAt: LocalDateTime,
    @get:JsonProperty("isLiked")
    val isLiked: Boolean = false,
    @get:JsonProperty("isCommentedByMe")
    val isCommentedByMe: Boolean = false,
    val imageUrls: List<String> = emptyList(),
    /** T1-2 Phase 5: post body 이미지 썸네일 (200px). imageUrls 와 길이 매치 — 없으면 원본 URL fallback. */
    val imageUrlsThumbnail: List<String>? = null,
    /** T1-2 Phase 5: post body 이미지 medium (720px). 상세 페이지 인라인 렌더용. */
    val imageUrlsMedium: List<String>? = null,
    /** T1-2 Phase 5: post body 이미지 viewer (1600px). 갤러리 모달 전체 화면 렌더용. */
    val imageUrlsViewer: List<String>? = null,
    /** T2: post body 이미지 WebP 썸네일. imageUrlsThumbnail 과 길이 매치 — WebP 미생성 시 null. */
    val imageUrlsThumbnailWebp: List<String?>? = null,
    val comments: List<CommunityCommentResponse> = emptyList(),
    @get:JsonProperty("isMine")
    val isMine: Boolean = false
)

data class CommunityPostCreateRequest(
    val categoryId: Long,
    @field:NotBlank(message = "제목을 입력해주세요")
    @field:Size(max = 100, message = "제목은 100자 이내여야 합니다")
    val title: String,
    @field:NotBlank(message = "내용을 입력해주세요")
    val content: String,
    val visibility: CommunityPost.Visibility = CommunityPost.Visibility.PUBLIC,
    val postType: CommunityPost.PostType = CommunityPost.PostType.GENERAL,
    val imageUrls: List<String> = emptyList()
)

data class CommunityCommentResponse(
    val id: Long,
    val postId: Long,
    val authorId: Long,
    val authorNickname: String?,
    val authorProfileUrl: String?,
    /** T1-1.3: author profile thumbnail (200px). */
    val authorProfileUrlThumbnail: String? = null,
    /** T1-1.3: author profile viewer (1600px). */
    val authorProfileUrlViewer: String? = null,
    val content: String,
    val createdAt: LocalDateTime,
    val parentCommentId: Long?,
    val likeCount: Int = 0,
    @get:JsonProperty("isLiked")
    val isLiked: Boolean = false,
    @get:JsonProperty("isRepliedByMe")
    val isRepliedByMe: Boolean = false,
    val depth: Int = 1,
    val replyCount: Int = 0,
    @get:JsonProperty("isMine")
    val isMine: Boolean = false,
    val postTitle: String? = null
)

data class CommunityCommentCreateRequest(
    @field:NotBlank(message = "댓글 내용을 입력해주세요")
    @field:Size(max = 1000, message = "댓글은 1000자 이내여야 합니다")
    val content: String,
    val parentCommentId: Long? = null
)

data class CommunityCategoryResponse(
    val id: Long,
    val name: String,
    val code: String,
    val postCount: Long = 0
)
