package com.goldpet.domain.emoticon.service

import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.emoticon.entity.Emoticon
import com.goldpet.domain.emoticon.entity.EmoticonPack
import com.goldpet.domain.emoticon.repository.EmoticonRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class EmoticonServiceTest {

    @Mock private lateinit var emoticonRepository: EmoticonRepository
    @Mock private lateinit var fileAttachmentLookupService: FileAttachmentLookupService

    @InjectMocks
    private lateinit var emoticonService: EmoticonService

    @Test
    fun `getById_shouldReturnEmoticon_whenExists`() {
        val pack = EmoticonPack(id = 1L, name = "Cute Animals", sortOrder = 0)
        val emoticon = Emoticon(id = 5L, pack = pack, imageUrl = "https://cdn.test/emo5.gif", sortOrder = 1)

        `when`(emoticonRepository.findById(5L)).thenReturn(Optional.of(emoticon))

        val result = emoticonService.getById(5L)

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(5L)
        assertThat(result.imageUrl).isEqualTo("https://cdn.test/emo5.gif")
        verify(emoticonRepository).findById(5L)
    }
}
