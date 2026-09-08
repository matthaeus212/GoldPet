package com.goldpet.domain.auth.service

import com.goldpet.domain.auth.entity.OAuthNonce
import com.goldpet.domain.auth.repository.OAuthNonceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class OAuthNonceServiceTest {

    @Mock private lateinit var repo: OAuthNonceRepository

    private lateinit var service: OAuthNonceService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = OAuthNonceService(repo)
    }

    @Test
    fun `issue persists new row with given provider and userId, returns UUID`() {
        val uuid = service.issue(userId = 42L, provider = "LINK_SUGGESTION")

        val captor = ArgumentCaptor.forClass(OAuthNonce::class.java)
        verify(repo).save(captor.capture())
        assertEquals(42L, captor.value.userId)
        assertEquals("LINK_SUGGESTION", captor.value.provider)
        assertEquals(uuid, captor.value.nonceUuid)
    }

    @Test
    fun `issue rejects blank provider`() {
        assertThrows<IllegalArgumentException> {
            service.issue(userId = 1L, provider = "")
        }
        verify(repo, never()).save(any())
    }

    @Test
    fun `consume returns true when tryConsume reports 1 row updated`() {
        val uuid = UUID.randomUUID()
        whenever(repo.tryConsume(uuid)).thenReturn(1)
        assertTrue(service.consume(uuid.toString()))
    }

    @Test
    fun `consume returns false when tryConsume reports 0 (replay or expired)`() {
        val uuid = UUID.randomUUID()
        whenever(repo.tryConsume(uuid)).thenReturn(0)
        assertFalse(service.consume(uuid.toString()))
    }

    @Test
    fun `consume returns false for malformed UUID without hitting DB`() {
        assertFalse(service.consume("not-a-uuid"))
        verify(repo, never()).tryConsume(any())
    }
}
