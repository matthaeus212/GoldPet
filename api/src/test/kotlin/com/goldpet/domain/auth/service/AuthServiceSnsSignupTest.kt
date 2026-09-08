package com.goldpet.domain.auth.service

import com.goldpet.config.crypto.EncryptionConfig
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.auth.dto.SnsSignupRequest
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Optional

class AuthServiceSnsSignupTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Mock
    private lateinit var systemSettingService: SystemSettingService

    @Mock
    private lateinit var goldService: GoldService

    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        EncryptionConfig("auth-sns-signup-test-key-32!!", "").validate()
        authService = AuthService(
            userRepository,
            AccountStatusGuard(jwtTokenProvider),
            org.mockito.kotlin.mock<DevLoginAccessPolicy>(),
            BCryptPasswordEncoder(),
            jwtTokenProvider,
            systemSettingService,
            goldService
        )
        whenever(systemSettingService.getBoolean("allow_signups", true)).thenReturn(true)
    }

    @Test
    fun `snsSignup should complete apple signup without collecting name`() {
        val user = oauthFirstTouchUser(id = 1L)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<User>())).thenAnswer { it.getArgument<User>(0) }
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("t")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("r")

        val request = SnsSignupRequest(
            provider = "apple",
            nickname = "애플유저",
            name = null,
            birthDate = null,
            gender = null,
            phoneNumber = null
        )

        val response = authService.snsSignup(1L, request)

        assertNull(user.name, "Apple 추가가입은 이름을 새로 수집하지 않아야 한다")
        assertTrue(response.user.signupCompleted)
    }

    private fun oauthFirstTouchUser(id: Long): User =
        User(
            id = id,
            email = "apple$id@privaterelay.appleid.com",
            oauthProvider = "apple",
            oauthId = "apple_$id",
            username = null,
            password = null,
            nickname = "Apple User",
            name = null,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null,
            signupCompletedAt = null
        )
}
