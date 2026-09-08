package com.goldpet.domain.experiment.service

import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.experiment.repository.ExperimentAssignmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * A/B 코호트 배정 서비스(plan §1 W1(1), V79).
 *
 * **write-once** 계약:
 * 1. 이미 할당된 행이 있으면 그 코호트를 반환(재계산 금지).
 * 2. 실험 비활성(`experiment.<key>.enabled`=false)이면 행을 만들지 않고 [COHORT_CONTROL] 반환.
 * 3. 활성이면 deterministic 버킷팅으로 코호트 결정 → `INSERT ON CONFLICT DO NOTHING` →
 *    승자 행을 **RE-SELECT** 하여 반환(동시 첫노출 레이스 → 동일 코호트 + 정확히 1행).
 *
 * splitPct/salt 런타임 변경은 **미할당 신규 유저에만** 적용된다(기존 행은 불변) — 어드민이
 * splitPct 를 바꿔도 기존 코호트는 retroactive 재버킷되지 않는다.
 *
 * serving(FriendService)·like_events 양쪽 모두 [getOrAssignCohort] 를 호출하여 같은 행을 read 한다.
 * 해시 재계산을 다른 곳에서 하지 말 것 — 이 서비스가 단일 출처.
 */
@Service
class ExperimentService(
    private val experimentAssignmentRepository: ExperimentAssignmentRepository,
    private val systemSettingService: SystemSettingService
) {
    companion object {
        const val COMPATIBILITY_KEY = "compatibility"
        const val COHORT_TREATMENT = "TREATMENT"
        const val COHORT_CONTROL = "CONTROL"
        /** like_events 등 read-only 소비자용 — 미노출(미할당) 유저는 NONE(A/B 분모 제외). */
        const val COHORT_NONE = "NONE"
        const val DEFAULT_SPLIT_PCT = 50
        const val DEFAULT_SALT_VERSION = 1

        /**
         * deterministic 버킷팅 — 순수 함수(테스트 가능).
         * `abs(hash("<userId>:<salt>")) % 100 < splitPct` → TREATMENT, else CONTROL.
         * 같은 (userId, salt) 는 항상 같은 코호트(레이스 경쟁자끼리도 동일).
         */
        fun bucketCohort(userId: Long, salt: String, splitPct: Int): String {
            val bucket = Math.abs("$userId:$salt".hashCode()) % 100
            return if (bucket < splitPct) COHORT_TREATMENT else COHORT_CONTROL
        }
    }

    /**
     * write-once 코호트 배정/조회. [Propagation.REQUIRES_NEW] 로 별도 read-write 트랜잭션에서 실행되어
     * FriendService 의 readOnly 트랜잭션 안에서 호출돼도 native INSERT 가 거부되지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun getOrAssignCohort(userId: Long, experimentKey: String = COMPATIBILITY_KEY): String {
        // 1. write-once: 기존 행이 승자.
        experimentAssignmentRepository.findByUserIdAndExperimentKey(userId, experimentKey)
            ?.let { return it.cohort }

        // 2. 실험 비활성 → 행 미생성, CONTROL.
        val enabled = systemSettingService.getBoolean("experiment.$experimentKey.enabled", false)
        if (!enabled) return COHORT_CONTROL

        // 3. 버킷팅 → write-once INSERT → 승자 RE-SELECT.
        val salt = systemSettingService.getString("experiment.$experimentKey.salt", "")
        val splitPct = systemSettingService.getInt("experiment.$experimentKey.split_pct", DEFAULT_SPLIT_PCT)
        val saltVersion = systemSettingService.getInt("experiment.$experimentKey.salt_version", DEFAULT_SALT_VERSION)
        val cohort = bucketCohort(userId, salt, splitPct)

        experimentAssignmentRepository.insertIfAbsent(userId, experimentKey, cohort, splitPct, saltVersion)

        // RE-SELECT: 레이스에서 진 쪽도 승자 행을 읽어 동일 코호트를 serve(정확히 1행).
        return experimentAssignmentRepository
            .findByUserIdAndExperimentKey(userId, experimentKey)
            ?.cohort
            ?: cohort
    }

    /**
     * **read-only** 코호트 조회 — like_events 등 비-serving 소비자용(plan §1 W1(2), guardrail #3).
     * 행을 절대 생성하지 않으며, 미할당(미노출) 유저는 [COHORT_NONE] 을 반환한다(A/B 분모 제외).
     * 노출 시점에 배정/serve 하는 [getOrAssignCohort] 와 달리, 이미 배정된 코호트를 그대로 읽기만 한다.
     * like_events 워커는 해시를 재계산하지 말고 이 메서드(또는 [getOrAssignCohort])로 같은 행을 read 할 것.
     */
    @Transactional(readOnly = true)
    fun readCohortOrNone(userId: Long, experimentKey: String = COMPATIBILITY_KEY): String =
        experimentAssignmentRepository.findByUserIdAndExperimentKey(userId, experimentKey)?.cohort
            ?: COHORT_NONE
}
