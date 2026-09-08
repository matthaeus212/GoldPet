// dev-login 허용 IP CRUD — 잠김 방지 가드와 광역 CIDR 거부를 고정한다
package com.goldpet.domain.auth.service

import com.goldpet.domain.auth.entity.DevLoginIpAllow
import com.goldpet.domain.auth.repository.DevLoginIpAllowRepository
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.exception.ConflictException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.Optional

class DevLoginIpAllowlistServiceTest {

    private lateinit var repository: DevLoginIpAllowRepository
    private lateinit var service: DevLoginIpAllowlistService

    private val myIp = "115.79.198.72"

    @BeforeEach
    fun setUp() {
        repository = mock()
        service = DevLoginIpAllowlistService(repository)
        whenever(repository.findByIpPattern(any())).thenReturn(Optional.empty())
        whenever(repository.save(any<DevLoginIpAllow>())).thenAnswer { it.getArgument(0) }
    }

    private fun entry(id: Long, pattern: String, enabled: Boolean = true, expiresAt: LocalDateTime? = null) =
        DevLoginIpAllow(id = id, ipPattern = pattern, label = "l$id", enabled = enabled, expiresAt = expiresAt)

    // ── 광역 CIDR 거부 ────────────────────────────────────────────────────────
    // 잠긴 사람은 급한 마음에 0.0.0.0/0 을 넣는다. 그 순간 통제가 사라지고, 아무도 다시 안 본다.

    @Test
    fun `0000-0 같은 광역 대역은 거부한다`() {
        val ex = assertThrows(BadRequestException::class.java) {
            service.create("0.0.0.0/0", "전체허용", null, adminId = 1L)
        }
        assertTrue(ex.message!!.contains("넓은"), ex.message)
        verify(repository, never()).save(any<DevLoginIpAllow>())
    }

    @Test
    fun `IPv4 는 24 보다 넓으면 거부하고 24 이하는 허용한다`() {
        assertThrows(BadRequestException::class.java) {
            service.create("113.0.0.0/8", "베트남 전체", null, adminId = 1L)
        }
        assertThrows(BadRequestException::class.java) {
            service.create("10.1.0.0/23", "약간 넓음", null, adminId = 1L)
        }
        // /24 는 허용
        service.create("10.1.2.0/24", "사무실", null, adminId = 1L)
        verify(repository).save(any<DevLoginIpAllow>())
    }

    @Test
    fun `IPv6 는 48 보다 넓으면 거부한다`() {
        assertThrows(BadRequestException::class.java) {
            service.create("2001:db8::/32", "너무 넓음", null, adminId = 1L)
        }
    }

    @Test
    fun `올바르지 않은 IP 는 거부한다`() {
        assertThrows(BadRequestException::class.java) {
            service.create("그냥문자열", "x", null, adminId = 1L)
        }
    }

    @Test
    fun `중복 IP 는 409`() {
        whenever(repository.findByIpPattern("1.2.3.4")).thenReturn(Optional.of(entry(1L, "1.2.3.4")))
        assertThrows(ConflictException::class.java) {
            service.create("1.2.3.4", "dup", null, adminId = 1L)
        }
    }

    // ── 자기잠김 가드 ─────────────────────────────────────────────────────────

    @Test
    fun `내 IP 를 비활성화하면 잠기므로 409 로 막는다`() {
        val mine = entry(1L, myIp)
        whenever(repository.findById(1L)).thenReturn(Optional.of(mine))
        // 변경 후 활성 엔트리에 내 IP 가 없다
        whenever(repository.findActive(any())).thenReturn(listOf(entry(2L, "203.0.113.1")))

        val ex = assertThrows(ConflictException::class.java) {
            service.update(
                id = 1L, label = null, enabled = false, expiresAt = null, clearExpiry = false,
                requesterIp = myIp, confirmSelfLockout = false
            )
        }
        assertTrue(ex.message!!.contains(myIp), ex.message)
    }

    @Test
    fun `confirmSelfLockout 이면 잠김을 감수하고 진행한다`() {
        val mine = entry(1L, myIp)
        whenever(repository.findById(1L)).thenReturn(Optional.of(mine))
        whenever(repository.findActive(any())).thenReturn(emptyList())

        service.update(
            id = 1L, label = null, enabled = false, expiresAt = null, clearExpiry = false,
            requesterIp = myIp, confirmSelfLockout = true
        )
        assertEquals(false, mine.enabled)
    }

    @Test
    fun `내 IP 가 CIDR 로 여전히 커버되면 다른 엔트리는 자유롭게 지울 수 있다`() {
        val other = entry(2L, "203.0.113.1")
        whenever(repository.findById(2L)).thenReturn(Optional.of(other))
        whenever(repository.findActive(any()))
            .thenReturn(listOf(entry(1L, "115.79.198.0/24"), other))  // 삭제 전
            .thenReturn(listOf(entry(1L, "115.79.198.0/24")))          // 삭제 후 — 내 IP 는 CIDR 로 커버

        service.delete(2L, requesterIp = myIp, confirmSelfLockout = false)
        verify(repository).delete(other)
    }

    // ── 마지막 엔트리 보호 ────────────────────────────────────────────────────

    @Test
    fun `마지막 활성 엔트리는 삭제할 수 없다`() {
        val only = entry(1L, myIp)
        whenever(repository.findById(1L)).thenReturn(Optional.of(only))
        whenever(repository.findActive(any())).thenReturn(listOf(only))

        val ex = assertThrows(ConflictException::class.java) {
            service.delete(1L, requesterIp = myIp, confirmSelfLockout = false)
        }
        assertTrue(ex.message!!.contains("킬스위치"), ex.message)
        verify(repository, never()).delete(any<DevLoginIpAllow>())
    }

    // ── 만료 ─────────────────────────────────────────────────────────────────

    @Test
    fun `만료된 엔트리는 비활성으로 본다`() {
        val expired = entry(1L, "1.2.3.4", expiresAt = LocalDateTime.now().minusMinutes(1))
        val live = entry(2L, "1.2.3.5", expiresAt = LocalDateTime.now().plusHours(1))
        assertEquals(false, expired.isActive())
        assertEquals(true, live.isActive())
    }
}
