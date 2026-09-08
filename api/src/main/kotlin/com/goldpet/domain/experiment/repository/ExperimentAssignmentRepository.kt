package com.goldpet.domain.experiment.repository

import com.goldpet.domain.experiment.entity.ExperimentAssignment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface ExperimentAssignmentRepository : JpaRepository<ExperimentAssignment, Long> {

    /** write-once 조회: 이미 할당된 행이 있으면 그 코호트가 승자(재계산 금지). */
    fun findByUserIdAndExperimentKey(userId: Long, experimentKey: String): ExperimentAssignment?

    /**
     * write-once 삽입 — `ON CONFLICT DO NOTHING` 로 동시 첫노출 레이스를 흡수한다.
     * 두 트랜잭션이 동시에 같은 (user_id, experiment_key) 를 삽입해도 정확히 1행만 남고,
     * 진 쪽은 조용히 0행 영향 → 호출부가 직후 [findByUserIdAndExperimentKey] 로 승자 행을 RE-SELECT.
     * 코호트는 deterministic 버킷팅이므로 경쟁자끼리도 동일 코호트가 보장된다.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO experiment_assignment
                (user_id, experiment_key, cohort, split_pct_at_assignment, salt_version, assigned_at)
            VALUES
                (:userId, :experimentKey, :cohort, :splitPct, :saltVersion, now())
            ON CONFLICT (user_id, experiment_key) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("userId") userId: Long,
        @Param("experimentKey") experimentKey: String,
        @Param("cohort") cohort: String,
        @Param("splitPct") splitPct: Int,
        @Param("saltVersion") saltVersion: Int
    ): Int

    /**
     * GDPR purge: removes the withdrawn user's experiment assignments.
     * Explicit because the deletion path (UserService.anonymizeAndDelete) anonymizes
     * rather than hard-deleting the users row, so the table's ON DELETE CASCADE FK
     * never fires.
     */
    @Modifying
    @Transactional
    @Query(
        value = "DELETE FROM experiment_assignment WHERE user_id = :userId",
        nativeQuery = true
    )
    fun deleteByUserId(@Param("userId") userId: Long): Int
}
