package com.goldpet.domain.common.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "file_attachments")
class FileAttachment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "owner_user_id", nullable = false)
    val ownerUserId: Long,

    @Column(name = "file_type", nullable = false)
    val fileType: String, // IMAGE, VIDEO, etc.

    @Column(name = "mime_type", nullable = false)
    val mimeType: String,

    @Column(nullable = false)
    val url: String,

    @Column(name = "original_file_name")
    val originalFileName: String? = null,

    @Column(name = "size_bytes")
    val sizeBytes: Long? = null,

    val width: Int? = null,
    val height: Int? = null,

    /** Variant URLs are mutable — backfilled after upload by the variant pipeline. */
    @Column(name = "thumbnail_url")
    var thumbnailUrl: String? = null,

    @Column(name = "medium_url")
    var mediumUrl: String? = null,

    /** 1600px-wide viewer image for walk photo feed; null until variant job runs. */
    @Column(name = "viewer_url")
    var viewerUrl: String? = null,

    /** T2: WebP thumbnail variant (200px). Null for rows uploaded before WebP pipeline. */
    @Column(name = "thumbnail_url_webp")
    var thumbnailUrlWebp: String? = null,

    /** T2: WebP medium variant (600px). Null for rows uploaded before WebP pipeline. */
    @Column(name = "medium_url_webp")
    var mediumUrlWebp: String? = null,

    /** T2: WebP viewer variant (1600px). Null for rows uploaded before WebP pipeline. */
    @Column(name = "viewer_url_webp")
    var viewerUrlWebp: String? = null,

    @Column(name = "duration_sec")
    val durationSec: Int? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * Bumped by updateVariantUrls/updateAllVariantUrls native statements. Used by the
     * FILE_ATTACHMENTS_RETRY backfill scope to enforce a cooldown on permanent failures.
     */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
