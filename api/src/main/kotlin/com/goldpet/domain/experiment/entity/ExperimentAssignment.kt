package com.goldpet.domain.experiment.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * write-once A/B 코호트 영속(plan §1 W1(1), V79).
 * 노출 시점에 1회 버킷팅된 결과를 보관 — 이후 serving(FriendService) + like_events 둘 다 이 행에서 cohort READ.
 * INSERT 는 [com.goldpet.domain.experiment.repository.ExperimentAssignmentRepository.insertIfAbsent]
 * 의 native `ON CONFLICT DO NOTHING` 로 수행되므로(레이스 안전), 이 엔티티는 주로 READ 용도.
 */
@Entity
@Table(name = "experiment_assignment")
class ExperimentAssignment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "experiment_key", nullable = false)
    val experimentKey: String,

    /** TREATMENT | CONTROL — 할당 후 불변. */
    @Column(name = "cohort", nullable = false)
    val cohort: String,

    @Column(name = "split_pct_at_assignment", nullable = false)
    val splitPctAtAssignment: Int,

    @Column(name = "salt_version", nullable = false)
    val saltVersion: Int,

    @Column(name = "assigned_at", nullable = false)
    val assignedAt: LocalDateTime = LocalDateTime.now()
)
