package com.goldpet.domain.gamification.streak.dto

import com.goldpet.domain.gamification.streak.entity.UserStreak
import java.time.LocalDate

/**
 * 스트릭 위젯용 읽기 응답 (GET /api/v1/streaks/me).
 * row 부재(콜드스타트) 시 [empty] 로 0-스트릭 표현.
 */
data class StreakResponse(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastActiveDate: LocalDate?,
    val freezeCount: Int,
    /** 오늘(KST) 이미 적격 산책을 했는지 — 위젯의 "오늘 완료" 표시용. */
    val activeToday: Boolean
) {
    companion object {
        fun from(streak: UserStreak, today: LocalDate): StreakResponse = StreakResponse(
            currentStreak = streak.currentStreak,
            longestStreak = streak.longestStreak,
            lastActiveDate = streak.lastActiveDate,
            freezeCount = streak.freezeCount,
            activeToday = streak.lastActiveDate == today
        )

        fun empty(): StreakResponse = StreakResponse(
            currentStreak = 0,
            longestStreak = 0,
            lastActiveDate = null,
            freezeCount = 0,
            activeToday = false
        )
    }
}
