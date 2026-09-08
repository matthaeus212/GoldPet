package com.goldpet.domain.notification.reengagement.service

import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.notification.reengagement.repository.ReengagementSendRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReengagementServiceTest {

    private lateinit var repo: ReengagementSendRepository
    private lateinit var sender: ReengagementSender
    private lateinit var settings: SystemSettingService

    @BeforeEach
    fun setUp() {
        repo = mock()
        sender = mock()
        settings = mock()
        whenever(settings.getInt(eq("reengage.batch.max"), any())).thenReturn(5000)
        whenever(settings.getInt(eq("reengage.dormancy.days"), any())).thenReturn(3)
        whenever(settings.getInt(eq("reengage.dormancy.max.days"), any())).thenReturn(14)
    }

    private fun serviceAt(localDateTime: LocalDateTime): ReengagementService {
        val clock = Clock.fixed(localDateTime.atZone(KST).toInstant(), KST)
        return ReengagementService(repo, sender, settings, clock)
    }

    // ── 콰이엇아워 ───────────────────────────────────────────────

    @Test
    fun `콰이엇아워 경계 — 21시 이후부터 09시 이전까지 억제된다`() {
        val svc = serviceAt(LocalDateTime.of(2026, 5, 30, 12, 0))
        assertFalse(svc.isQuietHours(LocalTime.of(20, 59)))
        assertTrue(svc.isQuietHours(LocalTime.of(21, 0)))
        assertTrue(svc.isQuietHours(LocalTime.of(2, 0)))
        assertTrue(svc.isQuietHours(LocalTime.of(8, 59)))
        assertFalse(svc.isQuietHours(LocalTime.of(9, 0)))
    }

    @Test
    fun `콰이엇아워(22시 KST)에는 후보 조회도 발송도 하지 않는다`() {
        val svc = serviceAt(LocalDateTime.of(2026, 5, 30, 22, 0))

        val sent = svc.runNudge(NudgeType.DORMANCY)

        assertEquals(0, sent)
        verify(repo, never()).findDormancyCandidateUserIds(any(), any(), any(), any())
        verify(sender, never()).reserveAndSend(any(), any(), any(), any(), any(), any())
    }

    // ── 발송 경로 ────────────────────────────────────────────────

    @Test
    fun `휴면 넛지 — 후보를 조회하고 토큰을 묶어 per-user 발송한다`() {
        val svc = serviceAt(LocalDateTime.of(2026, 5, 30, 18, 0))
        whenever(repo.findDormancyCandidateUserIds(any(), any(), eq(LocalDate.of(2026, 5, 30)), eq(5000)))
            .thenReturn(listOf(7L, 8L))
        whenever(repo.findActiveFcmTokens(listOf(7L, 8L)))
            .thenReturn(listOf(arrayOf<Any>(7L, "tok-7"), arrayOf<Any>(8L, "tok-8a"), arrayOf<Any>(8L, "tok-8b")))
        whenever(sender.reserveAndSend(any(), any(), any(), any(), any(), any())).thenReturn(true)

        val sent = svc.runNudge(NudgeType.DORMANCY)

        assertEquals(2, sent)
        verify(sender, times(1)).reserveAndSend(eq(7L), eq(listOf("tok-7")), eq(NudgeType.DORMANCY), any(), any(), any())
        verify(sender, times(1)).reserveAndSend(eq(8L), eq(listOf("tok-8a", "tok-8b")), eq(NudgeType.DORMANCY), any(), any(), any())
    }

    @Test
    fun `스트릭-위기 넛지 — 후보 없으면 0건 발송`() {
        val svc = serviceAt(LocalDateTime.of(2026, 5, 30, 20, 0))
        whenever(repo.findStreakAtRiskCandidateUserIds(eq(LocalDate.of(2026, 5, 30)), eq(5000)))
            .thenReturn(emptyList())

        val sent = svc.runNudge(NudgeType.STREAK_AT_RISK)

        assertEquals(0, sent)
        verify(sender, never()).reserveAndSend(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `dedup으로 이미 발송된 유저는 sent 카운트에서 제외된다`() {
        val svc = serviceAt(LocalDateTime.of(2026, 5, 30, 18, 0))
        whenever(repo.findDormancyCandidateUserIds(any(), any(), any(), any())).thenReturn(listOf(7L, 8L))
        whenever(repo.findActiveFcmTokens(any()))
            .thenReturn(listOf(arrayOf<Any>(7L, "tok-7"), arrayOf<Any>(8L, "tok-8")))
        // 7L 은 신규 발송(true), 8L 은 이미 발송됨(false) 시뮬.
        whenever(sender.reserveAndSend(eq(7L), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(sender.reserveAndSend(eq(8L), any(), any(), any(), any(), any())).thenReturn(false)

        val sent = svc.runNudge(NudgeType.DORMANCY)

        assertEquals(1, sent)
    }
}

private val KST: ZoneId = ZoneId.of("Asia/Seoul")
