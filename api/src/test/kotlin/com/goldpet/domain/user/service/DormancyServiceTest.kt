// 휴면 전환/해제 로직 검증 — 전환은 곧 로그인 차단이므로 안전장치를 고정한다 (STYLE-001)
package com.goldpet.domain.user.service

import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.common.exception.UnauthorizedException
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

class DormancyServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var systemSettingService: SystemSettingService
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var service: DormancyService

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        systemSettingService = mock()
        jwtTokenProvider = mock()
        service = DormancyService(userRepository, systemSettingService, jwtTokenProvider)

        whenever(systemSettingService.getString(eq(DormancyService.DORMANCY_ENABLED_KEY), any()))
            .thenReturn("true")
        whenever(systemSettingService.getString(eq(DormancyService.DORMANCY_DAYS_KEY), any()))
            .thenReturn("365")
    }

    private fun user(id: Long, status: UserStatus = UserStatus.ACTIVE, lastLogin: LocalDateTime? = null) = User(
        id = id,
        email = "u$id@example.com",
        oauthProvider = "LOCAL",
        oauthId = "LOCAL_u$id",
        username = "u$id",
        password = "pw",
        nickname = "u$id",
        name = null,
        birthDate = null,
        phoneNumber = null,
        gender = null,
        birthYear = null,
        mainLocationText = null,
        mainLocationGeom = null,
        profileImageUrl = null,
        status = status,
        lastLoginAt = lastLogin
    )

    // 킬스위치: 전환 배치의 실수는 그대로 사용자 락아웃이다. 플래그가 꺼져 있으면 아무도 건드리지 않는다.
    @Test
    fun `conversion is skipped entirely when the runtime flag is off`() {
        whenever(systemSettingService.getString(eq(DormancyService.DORMANCY_ENABLED_KEY), any()))
            .thenReturn("false")

        assertEquals(0, service.convertInactiveToDormant())

        verify(userRepository, never()).findDormancyCandidates(any(), any())
        verify(userRepository, never()).saveAll(any<List<User>>())
    }

    @Test
    fun `inactive users are converted to DORMANT with a dormantAt timestamp`() {
        val stale = user(1L, lastLogin = LocalDateTime.now().minusDays(400))
        whenever(userRepository.findDormancyCandidates(any(), any())).thenReturn(listOf(stale))

        assertEquals(1, service.convertInactiveToDormant())

        assertEquals(UserStatus.DORMANT, stale.status)
        assertEquals(true, stale.dormantAt != null)
        verify(userRepository).saveAll(listOf(stale))
    }

    // 컷오프가 설정된 일수 그대로여야 한다. 잘못 계산하면 멀쩡한 사용자가 통째로 휴면 처리된다.
    @Test
    fun `cutoff equals now minus the configured dormancy days`() {
        whenever(systemSettingService.getString(eq(DormancyService.DORMANCY_DAYS_KEY), any()))
            .thenReturn("30")
        whenever(userRepository.findDormancyCandidates(any(), any())).thenReturn(emptyList())

        val before = LocalDateTime.now().minusDays(30).minusSeconds(5)
        service.convertInactiveToDormant()
        val after = LocalDateTime.now().minusDays(30).plusSeconds(5)

        val captor = org.mockito.kotlin.argumentCaptor<LocalDateTime>()
        verify(userRepository).findDormancyCandidates(captor.capture(), any())
        val cutoff = captor.firstValue
        assertEquals(true, cutoff.isAfter(before) && cutoff.isBefore(after), "cutoff=$cutoff")
    }

    @Test
    fun `activation flips DORMANT back to ACTIVE and refreshes lastLoginAt`() {
        val dormant = user(7L, status = UserStatus.DORMANT).apply {
            dormantAt = LocalDateTime.now().minusDays(1)
        }
        whenever(jwtTokenProvider.parseDormantActivationUserId("tok")).thenReturn(7L)
        whenever(userRepository.findById(7L)).thenReturn(Optional.of(dormant))

        service.activate("tok")

        assertEquals(UserStatus.ACTIVE, dormant.status)
        assertNull(dormant.dormantAt)
        // 해제 직후 배치가 다시 휴면으로 되돌리면 안 된다.
        assertEquals(true, dormant.lastLoginAt != null)
        verify(userRepository).save(dormant)
    }

    // 위조/만료 토큰으로 남의 계정을 깨우지 못해야 한다.
    @Test
    fun `activation rejects an invalid or expired token`() {
        whenever(jwtTokenProvider.parseDormantActivationUserId("bad")).thenReturn(null)

        assertThrows(UnauthorizedException::class.java) { service.activate("bad") }

        verify(userRepository, never()).save(any<User>())
    }

    // 버튼 두 번 누름 = 실패가 아니다.
    @Test
    fun `activation on an already active account is a no-op`() {
        val active = user(9L, status = UserStatus.ACTIVE)
        whenever(jwtTokenProvider.parseDormantActivationUserId("tok")).thenReturn(9L)
        whenever(userRepository.findById(9L)).thenReturn(Optional.of(active))

        service.activate("tok")

        assertEquals(UserStatus.ACTIVE, active.status)
        verify(userRepository, never()).save(any<User>())
    }
}
