package com.goldpet.service.auth

import com.goldpet.config.crypto.EncryptionConfig
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.auth.entity.UserAuthProvider
import com.goldpet.domain.auth.repository.UserAuthProviderRepository
import com.goldpet.domain.auth.service.OAuthNonceService
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.*

/**
 * 기존 소셜 계정(user_auth_providers 매칭) 로그인 경로가 signupCompleted를 올바르게
 * 노출하는지 검증. 이 경로는 외부 OAuth HTTP 호출 뒤 handleOAuthLogin으로 들어오므로
 * handleOAuthLogin을 직접 호출해 단위 검증한다.
 */
class SocialLoginServiceTest {

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var jwtTokenProvider: JwtTokenProvider
    @Mock private lateinit var userAuthProviderRepository: UserAuthProviderRepository
    @Mock private lateinit var systemSettingService: SystemSettingService
    @Mock private lateinit var oauthNonceService: OAuthNonceService

    private lateinit var service: SocialLoginService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        EncryptionConfig("social-login-test-key-32-chars!!", "").validate()
        service = SocialLoginService(
            com.goldpet.domain.auth.service.AccountStatusGuard(jwtTokenProvider),
            userRepository, jwtTokenProvider, userAuthProviderRepository,
            systemSettingService, oauthNonceService,
            "test-jwt-secret-that-is-long-enough-for-hmac-sha-256"
        )
        // link-suggestion 활성 경로로 진입해야 user_auth_providers 매칭 분기를 탄다.
        whenever(systemSettingService.getBoolean("auth.link_suggestion.enabled", false)).thenReturn(true)
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("access")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh")
    }

    private fun socialUser(id: Long, signupCompletedAt: LocalDateTime?): User = User(
        id = id,
        email = "kakao$id@kakao.com",
        oauthProvider = "kakao",
        oauthId = "kakao_$id",
        username = null,
        password = null,
        nickname = "기존소셜", // OAuth 프로필에서 자동 채워진 표시명
        name = "홍길동",
        birthDate = null,
        phoneNumber = null,
        gender = null,
        birthYear = null,
        mainLocationText = null,
        mainLocationGeom = null,
        profileImageUrl = null,
        signupCompletedAt = signupCompletedAt
    )

    private fun loginExistingProvider(user: User) = service.handleOAuthLogin(
        provider = "kakao",
        oauthId = user.oauthId,
        email = user.email,      // 동일 값 → syncUserFields 변경 없음(save 미호출)
        nickname = user.nickname,
        profileImage = null,
        attributes = emptyMap(),
        skipEmailMatch = false
    )

    @Test
    fun `existing provider login exposes signupCompleted=true for onboarded user`() {
        val user = socialUser(7L, signupCompletedAt = LocalDateTime.now())
        whenever(userAuthProviderRepository.findByProviderAndProviderId("kakao", "kakao_7"))
            .thenReturn(Optional.of(UserAuthProvider(user = user, provider = "kakao", providerId = "kakao_7", isPrimary = true)))

        val response = loginExistingProvider(user)

        assertTrue(response.user.signupCompleted, "온보딩 완료한 기존 소셜 사용자는 true여야 한다")
    }

    @Test
    fun `existing provider login exposes signupCompleted=false for not-yet-onboarded user`() {
        val user = socialUser(8L, signupCompletedAt = null)
        whenever(userAuthProviderRepository.findByProviderAndProviderId("kakao", "kakao_8"))
            .thenReturn(Optional.of(UserAuthProvider(user = user, provider = "kakao", providerId = "kakao_8", isPrimary = true)))

        val response = loginExistingProvider(user)

        assertFalse(response.user.signupCompleted, "추가가입 미완료 사용자는 false여야 한다")
    }
}
