package com.goldpet.domain.experiment.service

import com.goldpet.domain.experiment.service.ExperimentService.Companion.COHORT_CONTROL
import com.goldpet.domain.experiment.service.ExperimentService.Companion.COHORT_TREATMENT
import com.goldpet.domain.experiment.service.ExperimentService.Companion.bucketCohort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A/B 버킷팅 순수 함수 단위테스트 (plan §3 단위 — 결정성 + ~50/50).
 * DB 불필요 — [ExperimentService.bucketCohort] 는 deterministic 순수 함수.
 */
class ExperimentServiceBucketingTest {

    private val salt = "v1-seed"

    @Test
    fun `same userId and salt always bucket to the same cohort (deterministic)`() {
        val first = bucketCohort(12345L, salt, 50)
        repeat(50) {
            assertThat(bucketCohort(12345L, salt, 50)).isEqualTo(first)
        }
    }

    @Test
    fun `split_pct 0 puts everyone in CONTROL`() {
        (1L..500L).forEach { id ->
            assertThat(bucketCohort(id, salt, 0)).isEqualTo(COHORT_CONTROL)
        }
    }

    @Test
    fun `split_pct 100 puts everyone in TREATMENT`() {
        (1L..500L).forEach { id ->
            assertThat(bucketCohort(id, salt, 100)).isEqualTo(COHORT_TREATMENT)
        }
    }

    @Test
    fun `split_pct 50 yields an approximately even split over many ids`() {
        val n = 5000L
        val treatment = (1L..n).count { bucketCohort(it, salt, 50) == COHORT_TREATMENT }
        val ratio = treatment.toDouble() / n
        // ~50/50 — 결정적 해시이므로 넉넉한 마진(0.40~0.60)으로 분포 sanity 만 확인.
        assertThat(ratio).isBetween(0.40, 0.60)
    }

    @Test
    fun `changing salt re-buckets the same id space (salt participates in hash)`() {
        // 다른 salt 는 같은 id 집합을 다르게 가른다 — 두 salt 결과가 100% 동일하지 않음을 확인.
        val ids = (1L..200L)
        val sameAsSaltA = ids.count { bucketCohort(it, "salt-A", 50) == bucketCohort(it, "salt-B", 50) }
        assertThat(sameAsSaltA).isLessThan(200)
    }
}
