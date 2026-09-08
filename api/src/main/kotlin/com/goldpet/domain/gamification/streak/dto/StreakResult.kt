package com.goldpet.domain.gamification.streak.dto

import java.time.LocalDate

/**
 * 산책 1회 기록 후의 스트릭 상태(커밋된 값). 병합 푸시(walk+streak) 구성을 위해 리스너로 반환.
 */
data class StreakResult(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastActiveDate: LocalDate,
    /** 이번 산책으로 스트릭이 증가했는지(같은날 중복 산책은 false). */
    val incremented: Boolean,
    /** 1일 공백을 프리즈로 메웠는지. */
    val freezeConsumed: Boolean,
    /** 공백+프리즈 없음으로 1로 리셋됐는지. */
    val reset: Boolean,
    /** longest_streak 신기록 갱신 여부. */
    val newRecord: Boolean
)
