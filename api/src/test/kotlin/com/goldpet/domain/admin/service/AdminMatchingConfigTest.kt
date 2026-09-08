package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.MatchingConfigRequest
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.repository.AppNoticeRepository
import com.goldpet.domain.common.service.SystemSettingService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * 어드민 매칭 궁합 점수 설정 — 검증/영속 단위테스트.
 */
class AdminMatchingConfigTest {

    private val systemSettingService: SystemSettingService = mock()
    private val service = AdminSystemService(
        systemSettingService = systemSettingService,
        adminUserRepository = mock<AdminUserRepository>(),
        appNoticeRepository = mock<AppNoticeRepository>(),
        passwordEncoder = mock<PasswordEncoder>(),
        adminAuditService = mock()
    )

    private fun req(
        enabled: Boolean = true,
        d: Double = 0.40, i: Double = 0.25, h: Double = 0.20, t: Double = 0.15, bonus: Double = 0.15
    ) = MatchingConfigRequest(enabled, d, i, h, t, bonus)

    @Test
    fun `getMatchingConfig returns stored values`() {
        whenever(systemSettingService.getBoolean(eq("match.compatibility.enabled"), any())).thenReturn(true)
        whenever(systemSettingService.getString(eq("match.score.w_distance"), any())).thenReturn("0.40")
        whenever(systemSettingService.getString(eq("match.score.w_interest"), any())).thenReturn("0.25")
        whenever(systemSettingService.getString(eq("match.score.w_hobby"), any())).thenReturn("0.20")
        whenever(systemSettingService.getString(eq("match.score.w_temperament"), any())).thenReturn("0.15")
        whenever(systemSettingService.getString(eq("profile.boost.rank_bonus"), any())).thenReturn("0.15")

        val cfg = service.getMatchingConfig()

        assertThat(cfg.enabled).isTrue()
        assertThat(cfg.weightDistance).isEqualTo(0.40)
        assertThat(cfg.weightTemperament).isEqualTo(0.15)
        assertThat(cfg.boostRankBonus).isEqualTo(0.15)
    }

    @Test
    fun `updateMatchingConfig persists all six keys when weights sum to 1`() {
        service.updateMatchingConfig(req())
        verify(systemSettingService).setValue(eq("match.compatibility.enabled"), eq("true"), anyOrNull())
        verify(systemSettingService).setValue(eq("match.score.w_distance"), any(), anyOrNull())
        verify(systemSettingService).setValue(eq("profile.boost.rank_bonus"), any(), anyOrNull())
        verify(systemSettingService, times(6)).setValue(any(), any(), anyOrNull())
    }

    @Test
    fun `updateMatchingConfig rejects when weight sum is not 1`() {
        assertThatThrownBy { service.updateMatchingConfig(req(d = 0.50)) } // sum = 1.10
            .isInstanceOf(BadRequestException::class.java)
        verify(systemSettingService, times(0)).setValue(any(), any(), anyOrNull())
    }

    @Test
    fun `updateMatchingConfig rejects weight out of 0 to 1 range`() {
        assertThatThrownBy { service.updateMatchingConfig(req(bonus = 1.5)) }
            .isInstanceOf(BadRequestException::class.java)
        verify(systemSettingService, times(0)).setValue(any(), any(), anyOrNull())
    }
}
