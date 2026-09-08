package com.goldpet.domain.metrics.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * append-only 좋아요/취소 이벤트 로그(plan §1 W1(2), V80).
 *
 * `likes` 테이블은 MUTABLE(relike/cancel 가 같은 행 status 를 뒤집음)이라 match-rate 퍼널을 거기서 파생할 수 없다.
 * LikeService.likeUser / unlikeUser(+ MatchService.likeUser 두 번째 seam) 시점에 1행씩 추가만 한다.
 *
 * - [isMatch]: 그 시점에 THIS 좋아요가 매치를 만들었는지(point-in-time 사실). "현재 매치 상태"로 읽지 말 것.
 * - [cohort]: experiment_assignment 에서 READ 한 값(TREATMENT/CONTROL/NONE). 해시 재계산 금지(guardrail #3).
 */
@Entity
@Table(name = "like_events")
class LikeEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "target_user_id", nullable = false)
    val targetUserId: Long,

    /** LIKE | CANCEL — [LikeEventAction] 의 name. */
    @Column(name = "action", nullable = false)
    val action: String,

    @Column(name = "is_match", nullable = false)
    val isMatch: Boolean,

    /** TREATMENT | CONTROL | NONE — experiment_assignment 에서 read(재계산 금지). */
    @Column(name = "cohort", nullable = false)
    val cohort: String,

    @Column(name = "source")
    val source: String? = null,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: LocalDateTime = LocalDateTime.now()
)

/** like_events.action 도메인 값. */
enum class LikeEventAction {
    LIKE,
    CANCEL
}
