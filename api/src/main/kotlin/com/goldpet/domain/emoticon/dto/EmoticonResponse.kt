package com.goldpet.domain.emoticon.dto

import com.goldpet.domain.emoticon.entity.Emoticon

data class EmoticonResponse(
    val id: Long,
    val name: String?,
    val category: String,
    val imageUrl: String,
    /** T1-2 Phase 4: FileAttachmentLookupService 로 배선. S3 이관된 이모티콘만 값 제공. */
    val imageUrlThumbnail: String? = null,
    /** T1-2 Phase 4: viewer variant (최대 해상도 원본 품질 근사). */
    val imageUrlViewer: String? = null,
    val sortOrder: Int,
    val packId: Long,
    val isActive: Boolean = true
) {
    companion object {
        fun from(
            emoticon: Emoticon,
            imageUrlThumbnail: String? = null,
            imageUrlViewer: String? = null,
        ): EmoticonResponse {
            return EmoticonResponse(
                id = emoticon.id,
                name = emoticon.name,
                category = emoticon.pack.name,
                imageUrl = emoticon.imageUrl,
                imageUrlThumbnail = imageUrlThumbnail,
                imageUrlViewer = imageUrlViewer,
                sortOrder = emoticon.sortOrder,
                packId = emoticon.pack.id,
                isActive = emoticon.isActive
            )
        }
    }
}
