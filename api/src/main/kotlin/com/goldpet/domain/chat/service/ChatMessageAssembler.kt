// 채팅 메시지 엔티티를 응답 DTO 로 조립하는 컴포넌트 (첨부/이모티콘/답장 프리뷰 배치 조회 포함)
package com.goldpet.domain.chat.service

import com.goldpet.domain.chat.dto.ChatMessageResponse
import com.goldpet.domain.chat.entity.ChatMessage
import com.goldpet.domain.chat.entity.ChatRoomParticipant
import com.goldpet.domain.common.repository.FileAttachmentRepository
import com.goldpet.domain.common.service.FileAttachmentLookupService
import com.goldpet.domain.emoticon.service.EmoticonService
import com.goldpet.domain.file.service.FileService
import org.springframework.stereotype.Component

/**
 * ARCH-003: 이 조립 로직은 원래 ChatRoomController 안에 있었다. 컨트롤러가 첨부/이모티콘
 * 리포지토리를 직접 주입받아 N+1 회피용 배치 조회와 presigned URL 해석까지 수행해, HTTP 계층에
 * 도메인 로직이 누수되고 ChatService 와 책임이 중복됐다.
 *
 * 메시지 목록과 단건이 **같은 규칙**으로 조립되도록 한 곳에 모은다.
 */
@Component
class ChatMessageAssembler(
    private val fileAttachmentRepository: FileAttachmentRepository,
    private val emoticonService: EmoticonService,
    private val fileService: FileService,
    private val fileAttachmentLookupService: FileAttachmentLookupService
) {
    private fun resolveFileUrl(url: String): String =
        if (url.startsWith("http")) url else fileService.getPresignedUrl(url)

    /**
     * 메시지 목록을 조립한다. 첨부·이모티콘·답장 프리뷰를 각각 한 번씩만 배치 조회한다.
     *
     * @param clientMsgIdFor 발신 직후 에코에서만 clientMsgId 를 실어 보내기 위한 훅.
     */
    fun assemble(
        messages: List<ChatMessage>,
        participants: List<ChatRoomParticipant>,
        clientMsgIdFor: (ChatMessage) -> String? = { null }
    ): List<ChatMessageResponse> {
        if (messages.isEmpty()) return emptyList()

        val fileAttachments = findAttachmentsById(messages.mapNotNull { it.fileId })
        val emoticons = emoticonService.getByIds(messages.mapNotNull { it.emoticonId })

        val replyToFileAttachments = findAttachmentsById(messages.mapNotNull { it.replyTo?.fileId })
        val replyToEmoticonIds = messages.mapNotNull { it.replyTo?.emoticonId }
        val replyToEmoticons =
            if (replyToEmoticonIds.isNotEmpty()) emoticonService.getByIds(replyToEmoticonIds) else emptyMap()

        // T1-1.4 배치 프리페치: 이모티콘 이미지 URL → FileAttachment 변형 lookup 을 미리 데운다.
        // T1-9 에서 seed 이모티콘도 S3 FileAttachment 경유로 전환돼 이 lookup 이 적중한다.
        val emoticonUrls =
            (emoticons.values.map { it.imageUrl } + replyToEmoticons.values.map { it.imageUrl }).distinct()
        if (emoticonUrls.isNotEmpty()) fileAttachmentLookupService.batchLookup(emoticonUrls)

        return messages.map { msg ->
            val attachment = msg.fileId?.let { fileAttachments[it] }
            val replyToAttachment = msg.replyTo?.fileId?.let { replyToFileAttachments[it] }
            ChatMessageResponse.from(
                msg,
                participants,
                attachment,
                msg.emoticonId?.let { emoticons[it] },
                replyToAttachment,
                msg.replyTo?.emoticonId?.let { replyToEmoticons[it] },
                resolvedFileUrl = attachment?.url?.let { resolveFileUrl(it) },
                resolvedReplyToFileUrl = replyToAttachment?.url?.let { resolveFileUrl(it) },
                variantResolver = fileAttachmentLookupService,
                clientMsgId = clientMsgIdFor(msg)
            )
        }
    }

    /** 단건 조립 — 발신 직후 응답/브로드캐스트용. 목록과 동일한 규칙을 재사용한다. */
    fun assembleOne(
        message: ChatMessage,
        participants: List<ChatRoomParticipant>,
        clientMsgId: String? = null
    ): ChatMessageResponse = assemble(listOf(message), participants) { clientMsgId }.first()

    private fun findAttachmentsById(ids: List<Long>) =
        if (ids.isEmpty()) emptyMap() else fileAttachmentRepository.findAllById(ids).associateBy { it.id }
}
