package com.goldpet.domain.walk.service

import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.file.service.FileService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider

class PhotoUrlSignerTest {

    @Mock
    private lateinit var fileService: FileService

    @Mock
    private lateinit var systemSettingService: SystemSettingService

    private val meterRegistry = SimpleMeterRegistry()

    private lateinit var signer: PhotoUrlSigner

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        val cacheProvider = mock<ObjectProvider<PerRequestSignerCache>>()
        whenever(cacheProvider.ifAvailable).thenReturn(null)
        signer = PhotoUrlSigner(fileService, systemSettingService, meterRegistry, cacheProvider, SignedUrlCache())
    }

    private fun enableSharedSignerCache() {
        whenever(systemSettingService.getString(eq(PhotoUrlSigner.FLAG_SHARED_SIGNER_CACHE_KEY), any()))
            .thenReturn("true")
    }

    private fun enableFlag() {
        whenever(systemSettingService.getString(eq(PhotoUrlSigner.FLAG_KEY), any()))
            .thenReturn("true")
    }

    private fun disableFlag() {
        whenever(systemSettingService.getString(eq(PhotoUrlSigner.FLAG_KEY), any()))
            .thenReturn("false")
    }

    private fun enableVariantFlags() {
        whenever(systemSettingService.getString(eq(PhotoUrlSigner.FLAG_KEY), any()))
            .thenReturn("true")
        whenever(systemSettingService.getString(eq(PhotoUrlSigner.FLAG_VARIANTS_KEY), any()))
            .thenReturn("true")
    }

    @Test
    fun `medium variant key returns presigned url when variants flag enabled`() {
        enableVariantFlags()
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789_medium.jpg"
        val expectedUrl = "https://minio.example.com/goldpet-private/$key?X-Amz-Signature=abc"
        whenever(fileService.getPresignedUrl(eq(key), eq(PhotoUrlSigner.PRESIGN_TTL_MINUTES)))
            .thenReturn(expectedUrl)

        val result = signer.signedMediumUrlOrNull(key)

        assertNotNull(result)
        assertTrue(result!!.contains("X-Amz-Signature="), "presigned URL must carry X-Amz-Signature")
        verify(fileService).getPresignedUrl(eq(key), eq(30L))
    }

    @Test
    fun `medium variant key returns null when variants flag disabled`() {
        whenever(systemSettingService.getString(eq(PhotoUrlSigner.FLAG_KEY), any()))
            .thenReturn("true")
        whenever(systemSettingService.getString(eq(PhotoUrlSigner.FLAG_VARIANTS_KEY), any()))
            .thenReturn("false")
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789_medium.jpg"

        val result = signer.signedMediumUrlOrNull(key)

        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    @Test
    fun `medium null key returns null`() {
        enableVariantFlags()
        val result = signer.signedMediumUrlOrNull(null)
        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    @Test
    fun `non-variant key returns null for medium signer`() {
        enableVariantFlags()
        val result = signer.signedMediumUrlOrNull("../../etc/passwd")
        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    @Test
    fun `uuid key with extension returns presigned url when flag enabled`() {
        enableFlag()
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"
        val expectedUrl = "https://minio.example.com/goldpet-private/$key?X-Amz-Signature=abc&X-Amz-Expires=1800"
        whenever(fileService.getPresignedUrl(eq(key), eq(PhotoUrlSigner.PRESIGN_TTL_MINUTES)))
            .thenReturn(expectedUrl)

        val result = signer.signedUrlOrNull(key)

        assertNotNull(result)
        assertTrue(result!!.contains("X-Amz-Signature="), "presigned URL must carry X-Amz-Signature")
        verify(fileService).getPresignedUrl(eq(key), eq(30L))
    }

    @Test
    fun `bare uuid without extension still matches regex`() {
        enableFlag()
        val key = "A1B2C3D4-E5F6-7890-ABCD-EF0123456789"
        val expectedUrl = "https://minio.example.com/goldpet-private/$key?X-Amz-Signature=xyz"
        whenever(fileService.getPresignedUrl(eq(key), eq(PhotoUrlSigner.PRESIGN_TTL_MINUTES)))
            .thenReturn(expectedUrl)

        val result = signer.signedUrlOrNull(key)

        assertNotNull(result)
    }

    @Test
    fun `http prefixed input returns null without signing`() {
        enableFlag()
        val result = signer.signedUrlOrNull("https://cdn.example.com/goldpet-public/avatar.jpg")

        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    @Test
    fun `null input returns null`() {
        val result = signer.signedUrlOrNull(null)
        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    @Test
    fun `blank input returns null`() {
        val result = signer.signedUrlOrNull("   ")
        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    @Test
    fun `empty string returns null`() {
        val result = signer.signedUrlOrNull("")
        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    @Test
    fun `non-uuid input returns null and does not sign`() {
        enableFlag()
        val result = signer.signedUrlOrNull("../../etc/passwd")

        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    @Test
    fun `legacy filename shape returns null`() {
        enableFlag()
        val result = signer.signedUrlOrNull("walk_12345.jpg")

        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    @Test
    fun `flag disabled returns null even for valid uuid`() {
        disableFlag()
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"

        val result = signer.signedUrlOrNull(key)

        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    @Test
    fun `flag default false is respected when setting missing`() {
        whenever(systemSettingService.getString(eq(PhotoUrlSigner.FLAG_KEY), any()))
            .thenAnswer { invocation -> invocation.getArgument<String>(1) }
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789.jpg"

        val result = signer.signedUrlOrNull(key)

        assertNull(result)
        verify(fileService, never()).getPresignedUrl(any<String>(), any<Long>())
    }

    // 회귀(iOS WKWebView 메모리): 응답마다 새로 서명하면 X-Amz-Date/Signature 가 달라져 URL 이 바뀌고,
    // 브라우저는 같은 사진을 새 리소스로 보고 재다운로드·재디코딩한다(라이브: 썸네일 1장이 31회 재다운로드).
    // 공유 캐시가 켜지면 같은 키는 창 동안 동일 URL 을 돌려주고 서명은 단 1회만 일어나야 한다.
    @Test
    fun `shared cache returns an identical url for the same key so the browser image cache hits`() {
        enableVariantFlags()
        enableSharedSignerCache()
        val key = "a1b2c3d4-e5f6-7890-abcd-ef0123456789_medium.jpg"
        var counter = 0
        whenever(fileService.getPresignedUrl(eq(key), any<Long>()))
            .thenAnswer { "https://s3.example.com/$key?X-Amz-Signature=sig${counter++}" }

        val first = signer.signedMediumUrlOrNull(key)
        val second = signer.signedMediumUrlOrNull(key)

        assertNotNull(first)
        assertEquals(first, second)
        verify(fileService, times(1)).getPresignedUrl(eq(key), any<Long>())
    }

    // 캐시가 키를 섞지 않아야 한다 — 서로 다른 변형은 각자의 URL 을 받는다.
    @Test
    fun `shared cache does not collide across different keys`() {
        enableVariantFlags()
        enableSharedSignerCache()
        val medium = "a1b2c3d4-e5f6-7890-abcd-ef0123456789_medium.jpg"
        val thumb = "a1b2c3d4-e5f6-7890-abcd-ef0123456789_thumb.jpg"
        whenever(fileService.getPresignedUrl(any<String>(), any<Long>()))
            .thenAnswer { "https://s3.example.com/${it.getArgument<String>(0)}?sig" }

        val mediumUrl = signer.signedMediumUrlOrNull(medium)
        val thumbUrl = signer.signedThumbUrlOrNull(thumb)

        assertTrue(mediumUrl!!.contains("_medium.jpg"))
        assertTrue(thumbUrl!!.contains("_thumb.jpg"))
    }

    // 캐시 창(20분)은 presign TTL(30분)보다 짧아야 한다 — 그래야 캐시에서 꺼낸 URL 도 최소 10분 유효하다.
    // 이 부등식이 깨지면 만료된 URL 을 계속 내보내 사진이 통째로 깨진다.
    @Test
    fun `shared cache window is shorter than the presign ttl`() {
        assertTrue(
            SignedUrlCache.WINDOW.toMinutes() < PhotoUrlSigner.PRESIGN_TTL_MINUTES,
            "cache window(${SignedUrlCache.WINDOW.toMinutes()}m) must be < presign TTL(${PhotoUrlSigner.PRESIGN_TTL_MINUTES}m)"
        )
    }
}
