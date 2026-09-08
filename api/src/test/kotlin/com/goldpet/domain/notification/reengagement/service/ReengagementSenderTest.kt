package com.goldpet.domain.notification.reengagement.service

import com.goldpet.domain.notification.reengagement.entity.ReengagementSend
import com.goldpet.domain.notification.reengagement.repository.ReengagementSendRepository
import com.goldpet.domain.notification.service.FcmMulticastResult
import com.goldpet.domain.notification.service.FcmPushSender
import com.goldpet.domain.user.repository.UserDeviceRepository
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
import java.time.LocalDate

class ReengagementSenderTest {

    private lateinit var repo: ReengagementSendRepository
    private lateinit var fcm: FcmPushSender
    private lateinit var deviceRepo: UserDeviceRepository
    private lateinit var sender: ReengagementSender

    private val today = LocalDate.of(2026, 5, 30)
    private val tokens = listOf("tok-1", "tok-2")

    @BeforeEach
    fun setUp() {
        repo = mock()
        fcm = mock()
        deviceRepo = mock()
        sender = ReengagementSender(repo, fcm, deviceRepo)
    }

    @Test
    fun `이미 오늘 발송된 유저는 dedup으로 재발송하지 않는다`() {
        whenever(repo.existsByUserIdAndSendDate(7L, today)).thenReturn(true)

        val result = sender.reserveAndSend(7L, tokens, NudgeType.DORMANCY, today, "t", "m")

        assertFalse(result)
        verify(repo, never()).save(any<ReengagementSend>())
        verify(fcm, never()).sendToMultipleDetailed(any(), any(), any(), any())
    }

    @Test
    fun `신규 발송은 dedup row 저장 후 multicast 송신한다`() {
        whenever(repo.existsByUserIdAndSendDate(7L, today)).thenReturn(false)
        whenever(fcm.sendToMultipleDetailed(any(), any(), any(), any()))
            .thenReturn(FcmMulticastResult(successCount = 2, invalidTokens = emptyList(), totalSent = 2))

        val result = sender.reserveAndSend(7L, tokens, NudgeType.STREAK_AT_RISK, today, "title", "msg")

        assertTrue(result)
        verify(repo, times(1)).save(any<ReengagementSend>())
        verify(fcm, times(1)).sendToMultipleDetailed(eq(tokens), eq("title"), eq("msg"), any())
    }

    @Test
    fun `invalid 토큰은 deactivate 된다`() {
        whenever(repo.existsByUserIdAndSendDate(7L, today)).thenReturn(false)
        whenever(fcm.sendToMultipleDetailed(any(), any(), any(), any()))
            .thenReturn(FcmMulticastResult(successCount = 1, invalidTokens = listOf("tok-2"), totalSent = 2))

        sender.reserveAndSend(7L, tokens, NudgeType.DORMANCY, today, "t", "m")

        verify(deviceRepo, times(1)).deactivateByFcmToken("tok-2")
    }

    @Test
    fun `토큰이 없으면 dedup 저장도 송신도 하지 않는다`() {
        val result = sender.reserveAndSend(7L, emptyList(), NudgeType.DORMANCY, today, "t", "m")

        assertFalse(result)
        verify(repo, never()).existsByUserIdAndSendDate(any(), any())
        verify(repo, never()).save(any<ReengagementSend>())
        verify(fcm, never()).sendToMultipleDetailed(any(), any(), any(), any())
    }

    @Test
    fun `송신 예외는 삼켜져 dedup을 유지하고 true를 반환한다`() {
        whenever(repo.existsByUserIdAndSendDate(7L, today)).thenReturn(false)
        whenever(fcm.sendToMultipleDetailed(any(), any(), any(), any()))
            .thenThrow(RuntimeException("fcm down"))

        val result = sender.reserveAndSend(7L, tokens, NudgeType.DORMANCY, today, "t", "m")

        assertTrue(result) // dedup 유지 (중복 방지 우선)
        verify(repo, times(1)).save(any<ReengagementSend>())
    }
}
