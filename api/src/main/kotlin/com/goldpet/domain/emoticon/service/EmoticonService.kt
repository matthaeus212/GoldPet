package com.goldpet.domain.emoticon.service

import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.emoticon.dto.EmoticonResponse
import com.goldpet.domain.emoticon.entity.Emoticon
import com.goldpet.domain.emoticon.repository.EmoticonRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class EmoticonService(
    private val emoticonRepository: EmoticonRepository,
    private val fileAttachmentLookupService: FileAttachmentLookupService,
) {
    @Cacheable("emoticons")
    fun getActiveEmoticons(): List<EmoticonResponse> {
        val emoticons = emoticonRepository.findAllByIsActiveTrueOrderBySortOrder()
        fileAttachmentLookupService.batchLookup(emoticons.map { it.imageUrl })
        return emoticons.map {
            EmoticonResponse.from(
                it,
                imageUrlThumbnail = fileAttachmentLookupService.thumbnailUrlFor(it.imageUrl),
                imageUrlViewer = fileAttachmentLookupService.viewerUrlFor(it.imageUrl),
            )
        }
    }

    fun getById(id: Long): Emoticon? {
        return emoticonRepository.findById(id).orElse(null)
    }

    fun getByIds(ids: List<Long>): Map<Long, Emoticon> {
        if (ids.isEmpty()) return emptyMap()
        return emoticonRepository.findAllByIdIn(ids).associateBy { it.id }
    }
}
