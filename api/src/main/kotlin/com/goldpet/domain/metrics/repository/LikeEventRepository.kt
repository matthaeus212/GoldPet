package com.goldpet.domain.metrics.repository

import com.goldpet.domain.metrics.entity.LikeEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * append-only like_events 저장소(V80, W1(2)).
 * 기록은 [LikeEvent] 저장(insert)만 사용한다. 코호트별 match-rate 퍼널 집계(native SQL)는 T4 에서 추가.
 *
 * 삭제 경로(FIX-B):
 * - [deleteByUserParticipation]: 탈퇴(anonymizeAndDelete — hard-delete 안 함 → CASCADE 미발동) 시
 *   유저가 행위자(user_id)이거나 대상(target_user_id)인 모든 행을 purge (GDPR erasure + graph edge 제거).
 * - [deleteByOccurredAtBefore]: TTL 보존(~13개월) 야간 정리.
 */
interface LikeEventRepository : JpaRepository<LikeEvent, Long> {

    /**
     * 탈퇴 유저가 관여한 모든 like_events 행 삭제 — 행위자(user_id) 또는 대상(target_user_id).
     * target_user_id 에는 FK/CASCADE 가 없으므로(분석 그래프 엣지) 명시 OR 조건으로 함께 지운다.
     * @return 삭제된 행 수.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM LikeEvent e WHERE e.userId = :userId OR e.targetUserId = :userId")
    fun deleteByUserParticipation(@Param("userId") userId: Long): Int

    /**
     * occurred_at 이 [cutoff] 이전인 행 삭제 (TTL 보존 정리).
     * @return 삭제된 행 수.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM LikeEvent e WHERE e.occurredAt < :cutoff")
    fun deleteByOccurredAtBefore(@Param("cutoff") cutoff: LocalDateTime): Int
}
