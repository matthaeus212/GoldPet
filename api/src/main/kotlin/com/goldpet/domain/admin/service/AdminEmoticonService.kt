package com.goldpet.domain.admin.service

import com.goldpet.domain.emoticon.dto.EmoticonPackResponse
import com.goldpet.domain.emoticon.dto.EmoticonResponse
import com.goldpet.domain.emoticon.entity.Emoticon
import com.goldpet.domain.emoticon.repository.EmoticonPackRepository
import com.goldpet.domain.emoticon.repository.EmoticonRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class CreateEmoticonRequest(
    val name: String,
    val packId: Long,
    val imageUrl: String,
    val sortOrder: Int = 0
)

data class UpdateEmoticonRequest(
    val name: String? = null,
    val packId: Long? = null,
    val imageUrl: String? = null,
    val sortOrder: Int? = null
)

@Service
@Transactional(readOnly = true)
class AdminEmoticonService(
    private val emoticonRepository: EmoticonRepository,
    private val emoticonPackRepository: EmoticonPackRepository
) {
    fun getAllEmoticons(): List<EmoticonResponse> {
        return emoticonRepository.findAllByOrderBySortOrder()
            .map { EmoticonResponse.from(it) }
    }

    fun getAllPacks(): List<EmoticonPackResponse> {
        return emoticonPackRepository.findAllByOrderBySortOrder()
            .map { EmoticonPackResponse.from(it) }
    }

    @Transactional
    @CacheEvict("emoticons", allEntries = true)
    fun createEmoticon(request: CreateEmoticonRequest): EmoticonResponse {
        val pack = emoticonPackRepository.findById(request.packId)
            .orElseThrow { IllegalArgumentException("존재하지 않는 팩입니다: ${request.packId}") }

        val emoticon = Emoticon(
            pack = pack,
            name = request.name,
            imageUrl = request.imageUrl,
            sortOrder = request.sortOrder
        )
        return EmoticonResponse.from(emoticonRepository.save(emoticon))
    }

    @Transactional
    @CacheEvict("emoticons", allEntries = true)
    fun updateEmoticon(id: Long, request: UpdateEmoticonRequest): EmoticonResponse {
        val emoticon = emoticonRepository.findById(id)
            .orElseThrow { IllegalArgumentException("존재하지 않는 이모티콘입니다: $id") }

        request.name?.let { emoticon.name = it }
        request.imageUrl?.let { emoticon.imageUrl = it }
        request.sortOrder?.let { emoticon.sortOrder = it }
        request.packId?.let { packId ->
            val pack = emoticonPackRepository.findById(packId)
                .orElseThrow { IllegalArgumentException("존재하지 않는 팩입니다: $packId") }
            emoticon.pack = pack
        }

        return EmoticonResponse.from(emoticonRepository.save(emoticon))
    }

    @Transactional
    @CacheEvict("emoticons", allEntries = true)
    fun deleteEmoticon(id: Long) {
        if (!emoticonRepository.existsById(id)) {
            throw IllegalArgumentException("존재하지 않는 이모티콘입니다: $id")
        }
        emoticonRepository.deleteById(id)
    }

    @Transactional
    @CacheEvict("emoticons", allEntries = true)
    fun toggleActive(id: Long): EmoticonResponse {
        val emoticon = emoticonRepository.findById(id)
            .orElseThrow { IllegalArgumentException("존재하지 않는 이모티콘입니다: $id") }
        emoticon.isActive = !emoticon.isActive
        return EmoticonResponse.from(emoticonRepository.save(emoticon))
    }
}
