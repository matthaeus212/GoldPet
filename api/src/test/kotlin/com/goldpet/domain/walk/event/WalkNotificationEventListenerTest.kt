package com.goldpet.domain.walk.event

import com.goldpet.domain.gamification.streak.dto.StreakResult
import com.goldpet.domain.gamification.streak.service.StreakService
import com.goldpet.domain.notification.service.NotificationService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class WalkNotificationEventListenerTest {

    private lateinit var notificationService: NotificationService
    private lateinit var streakService: StreakService
    private lateinit var listener: WalkNotificationEventListener

    @BeforeEach
    fun setUp() {
        notificationService = mock()
        streakService = mock()
        listener = WalkNotificationEventListener(notificationService, streakService)
    }

    private fun event(goldReward: Int, distanceKm: Double = 2.5) =
        WalkCompletedEvent(
            source = this,
            walkId = 42L,
            userId = 7L,
            followedCourseId = null,
            distanceKm = distanceKm,
            goldReward = goldReward
        )

    private fun streak(current: Int) = StreakResult(
        currentStreak = current,
        longestStreak = current,
        lastActiveDate = LocalDate.of(2026, 5, 30),
        incremented = true,
        freezeConsumed = false,
        reset = false,
        newRecord = true
    )

    @Test
    fun `스트릭 갱신 후 보상-요약+스트릭 병합 푸시를 발송한다`() {
        whenever(streakService.recordWalk(7L)).thenReturn(streak(current = 3))

        listener.onWalkCompleted(event(goldReward = 30, distanceKm = 2.5))

        verify(streakService, times(1)).recordWalk(7L)
        // 병합: streakDays 가 currentStreak(3) 으로 전달되어 단일 푸시 발송.
        verify(notificationService, times(1))
            .notifyWalkCompleted(eq(7L), eq(42L), eq(2.5), eq(30), eq(3))
    }

    @Test
    fun `goldReward 0이면 스트릭은 갱신하되 푸시는 발송하지 않는다`() {
        whenever(streakService.recordWalk(7L)).thenReturn(streak(current = 1))

        listener.onWalkCompleted(event(goldReward = 0))

        verify(streakService, times(1)).recordWalk(7L)
        verify(notificationService, never())
            .notifyWalkCompleted(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `스트릭 갱신 실패해도 보상-요약 푸시는 streakDays 없이 발송된다`() {
        doThrow(RuntimeException("db down")).whenever(streakService).recordWalk(7L)

        listener.onWalkCompleted(event(goldReward = 30, distanceKm = 2.5))

        verify(notificationService, times(1))
            .notifyWalkCompleted(eq(7L), eq(42L), eq(2.5), eq(30), isNull())
    }

    @Test
    fun `푸시 발송 실패는 best-effort로 삼켜져 예외가 전파되지 않는다`() {
        whenever(streakService.recordWalk(7L)).thenReturn(streak(current = 2))
        doThrow(RuntimeException("fcm down"))
            .whenever(notificationService)
            .notifyWalkCompleted(any(), any(), any(), any(), anyOrNull())

        assertDoesNotThrow { listener.onWalkCompleted(event(goldReward = 30)) }
    }
}
