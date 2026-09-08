package com.goldpet.domain.chat.repository

import com.goldpet.domain.chat.entity.ChatMessage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    // ─────────────────────────────────────────────────────────────────────────
    // Legacy (admin/internal): hidden_at / deleted_at 무관 전체 조회.
    // 사용자 노출 경로는 아래 findVisible* 메서드 사용.
    // ─────────────────────────────────────────────────────────────────────────

    @EntityGraph(attributePaths = ["sender", "replyTo", "replyTo.sender"])
    fun findByChatRoomIdOrderByCreatedAtAsc(chatRoomId: Long, pageable: Pageable): List<ChatMessage>
    @EntityGraph(attributePaths = ["sender", "replyTo", "replyTo.sender"])
    fun findByChatRoomIdOrderByCreatedAtDesc(chatRoomId: Long, pageable: Pageable): Page<ChatMessage>
    fun findTopByChatRoomIdOrderByCreatedAtDesc(chatRoomId: Long): ChatMessage?
    fun deleteByChatRoomId(chatRoomId: Long)

    @Query("SELECT m FROM ChatMessage m WHERE m.sender.id = :userId")
    fun findAllBySenderId(@Param("userId") userId: Long): List<ChatMessage>

    /**
     * SEC-004 (W1a) — file resolve IDOR 방어용 참여 검사.
     * `:fileIds` 중, 조회자([viewerId])가 참여 중인 채팅방의 메시지가 참조하는 fileId 만 반환.
     * FileAttachmentLookupService.resolveMany 가 소유자 필터로 걸러내지 못한(=타인 소유)
     * 첨부에 대해 "같은 방 참여자면 열람 허용" 접근권을 판정한다.
     */
    @Query("""
        SELECT DISTINCT m.fileId FROM ChatMessage m
         WHERE m.fileId IN :fileIds
           AND EXISTS (
               SELECT 1 FROM ChatRoomParticipant p
                WHERE p.chatRoom.id = m.chatRoom.id
                  AND p.user.id = :viewerId
           )
    """)
    fun findAccessibleFileIds(
        @Param("viewerId") viewerId: Long,
        @Param("fileIds") fileIds: Collection<Long>
    ): List<Long>

    @EntityGraph(attributePaths = ["sender"])
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.createdAt > :since ORDER BY m.createdAt ASC")
    fun findByChatRoomIdAndCreatedAtAfter(
        @Param("roomId") roomId: Long,
        @Param("since") since: LocalDateTime
    ): List<ChatMessage>

    @EntityGraph(attributePaths = ["sender"])
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId ORDER BY m.createdAt ASC")
    fun findAllByChatRoomId(@Param("roomId") roomId: Long): List<ChatMessage>

    // ─────────────────────────────────────────────────────────────────────────
    // V68 BLOCKER #5 (Apple Guideline 1.2) — 사용자 노출 경로 전용.
    // hidden_at IS NULL AND deleted_at IS NULL 메시지만 반환.
    // Admin 처리/감사 경로는 위 legacy 메서드 또는 findById 직접 사용.
    // ─────────────────────────────────────────────────────────────────────────

    @EntityGraph(attributePaths = ["sender", "replyTo", "replyTo.sender"])
    @Query("""
        SELECT m FROM ChatMessage m
         WHERE m.chatRoom.id = :roomId
           AND m.hiddenAt IS NULL
           AND m.deletedAt IS NULL
         ORDER BY m.createdAt DESC
    """)
    fun findVisibleByChatRoomIdOrderByCreatedAtDesc(
        @Param("roomId") roomId: Long,
        pageable: Pageable
    ): Page<ChatMessage>

    @EntityGraph(attributePaths = ["sender"])
    @Query("""
        SELECT m FROM ChatMessage m
         WHERE m.chatRoom.id = :roomId
           AND m.createdAt > :since
           AND m.hiddenAt IS NULL
           AND m.deletedAt IS NULL
         ORDER BY m.createdAt ASC
    """)
    fun findVisibleByChatRoomIdAndCreatedAtAfter(
        @Param("roomId") roomId: Long,
        @Param("since") since: LocalDateTime
    ): List<ChatMessage>

    @Query("""
        SELECT m FROM ChatMessage m
         WHERE m.chatRoom.id = :roomId
           AND m.hiddenAt IS NULL
           AND m.deletedAt IS NULL
         ORDER BY m.createdAt DESC
         LIMIT 1
    """)
    fun findTopVisibleByChatRoomId(@Param("roomId") roomId: Long): ChatMessage?
}
