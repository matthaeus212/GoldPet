package com.goldpet.domain.gamification.streak.service

import com.goldpet.domain.gamification.streak.entity.UserStreak
import com.goldpet.domain.gamification.streak.repository.UserStreakRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

class StreakServiceTest {

    private lateinit var repo: UserStreakRepository
    private lateinit var service: StreakService

    @BeforeEach
    fun setUp() {
        repo = mock()
        service = StreakService(repo)
    }

    private fun streak(
        last: LocalDate?,
        current: Int = 0,
        longest: Int = 0,
        freeze: Int = 0
    ) = UserStreak(
        userId = 7L,
        currentStreak = current,
        longestStreak = longest,
        lastActiveDate = last,
        freezeCount = freeze
    )

    // ── applyTransition: 순수 상태 전이 ──────────────────────────────

    @Test
    fun `어제 활동이면 스트릭이 1 증가한다`() {
        val today = LocalDate.of(2026, 5, 30)
        val s = streak(last = today.minusDays(1), current = 3, longest = 5, freeze = 1)

        val r = service.applyTransition(s, today)

        assertEquals(4, r.currentStreak)
        assertTrue(r.incremented)
        assertFalse(r.freezeConsumed)
        assertFalse(r.reset)
        assertEquals(today, s.lastActiveDate)
    }

    @Test
    fun `같은 날 중복 산책은 무변화다`() {
        val today = LocalDate.of(2026, 5, 30)
        val s = streak(last = today, current = 3, longest = 5, freeze = 1)

        val r = service.applyTransition(s, today)

        assertEquals(3, r.currentStreak)
        assertFalse(r.incremented)
        assertEquals(1, s.freezeCount) // 프리즈 보존
    }

    @Test
    fun `1일 공백은 같은 주 프리즈로 메워 스트릭 유지한다`() {
        // 2026-05-28(목) → 2026-05-30(토): 같은 ISO week(W22), 1일(29금) 공백.
        val today = LocalDate.of(2026, 5, 30)
        val s = streak(last = LocalDate.of(2026, 5, 28), current = 3, longest = 3, freeze = 1)

        val r = service.applyTransition(s, today)

        assertEquals(4, r.currentStreak)
        assertTrue(r.freezeConsumed)
        assertFalse(r.reset)
        assertEquals(0, s.freezeCount)
        assertTrue(r.newRecord)
    }

    @Test
    fun `1일 공백이라도 프리즈가 없으면 스트릭이 1로 리셋된다`() {
        val today = LocalDate.of(2026, 5, 30)
        val s = streak(last = LocalDate.of(2026, 5, 28), current = 3, longest = 5, freeze = 0)

        val r = service.applyTransition(s, today)

        assertEquals(1, r.currentStreak)
        assertTrue(r.reset)
        assertFalse(r.freezeConsumed)
        assertEquals(5, r.longestStreak) // longest 보존
    }

    @Test
    fun `새 ISO week 진입 시 프리즈가 1로 재충전되어 주 경계 1일 공백을 메운다`() {
        // 2026-05-23(토, W21) → 2026-05-25(월, W22): 주 변경 + 1일(24일) 공백.
        val today = LocalDate.of(2026, 5, 25)
        val s = streak(last = LocalDate.of(2026, 5, 23), current = 4, longest = 4, freeze = 0)

        val r = service.applyTransition(s, today)

        assertEquals(5, r.currentStreak) // 재충전된 프리즈로 유지+증가
        assertTrue(r.freezeConsumed)
        assertEquals(0, s.freezeCount)
    }

    @Test
    fun `2일 이상 공백은 프리즈가 있어도 리셋된다`() {
        // 3일 공백(daysBetween == 3) — 단일 프리즈로 커버 불가.
        val today = LocalDate.of(2026, 5, 30)
        val s = streak(last = LocalDate.of(2026, 5, 27), current = 9, longest = 9, freeze = 1)

        val r = service.applyTransition(s, today)

        assertEquals(1, r.currentStreak)
        assertTrue(r.reset)
        assertFalse(r.freezeConsumed)
    }

    // ── recordWalk: 콜드스타트 + KST 경계 ──────────────────────────

    @Test
    fun `콜드스타트 — row 부재 시 lazy-create로 스트릭 1을 생성한다`() {
        whenever(repo.findByUserId(7L)).thenReturn(null)
        whenever(repo.save(any<UserStreak>())).thenAnswer { it.arguments[0] }
        val clock = fixedKst(LocalDateTime.of(2026, 5, 30, 12, 0))
        service = StreakService(repo, clock)

        val r = service.recordWalk(7L)

        assertEquals(1, r.currentStreak)
        assertEquals(1, r.longestStreak)
        assertTrue(r.incremented)
        assertTrue(r.newRecord)
        verify(repo, times(1)).save(any<UserStreak>())
    }

    @Test
    fun `KST 자정 경계 — 23시59분 다음 00시01분 산책은 다른 날로 연속 처리된다`() {
        // 직전 활동 2026-05-30. 00:01 KST 2026-05-31 산책 → KST 로는 5/31 (UTC 였다면 5/30 라 무변화).
        val existing = streak(last = LocalDate.of(2026, 5, 30), current = 2, longest = 2, freeze = 1)
        whenever(repo.findByUserId(7L)).thenReturn(existing)
        val clock = fixedKst(LocalDateTime.of(2026, 5, 31, 0, 1))
        service = StreakService(repo, clock)

        val r = service.recordWalk(7L)

        assertEquals(3, r.currentStreak)
        assertTrue(r.incremented)
        assertEquals(LocalDate.of(2026, 5, 31), existing.lastActiveDate)
    }

    @Test
    fun `같은 날 두 번째 산책은 recordWalk 에서도 무변화다`() {
        val existing = streak(last = LocalDate.of(2026, 5, 30), current = 4, longest = 6, freeze = 1)
        whenever(repo.findByUserId(7L)).thenReturn(existing)
        val clock = fixedKst(LocalDateTime.of(2026, 5, 30, 23, 59))
        service = StreakService(repo, clock)

        val r = service.recordWalk(7L)

        assertEquals(4, r.currentStreak)
        assertFalse(r.incremented)
        verify(repo, never()).save(any<UserStreak>()) // 기존 엔티티 dirty-checking, 강제 save 없음
    }

    private fun fixedKst(localDateTime: LocalDateTime): Clock =
        Clock.fixed(localDateTime.atZone(StreakService.KST).toInstant(), StreakService.KST)
}
