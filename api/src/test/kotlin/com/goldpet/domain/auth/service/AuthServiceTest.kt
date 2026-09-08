package com.goldpet.domain.auth.service

import com.goldpet.domain.common.exception.*
import com.goldpet.config.crypto.EncryptionConfig
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.domain.auth.dto.LoginRequest
import com.goldpet.domain.auth.dto.SignupRequest
import com.goldpet.domain.auth.dto.SnsSignupRequest
import java.time.LocalDateTime
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.*

class AuthServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Mock
    private lateinit var systemSettingService: SystemSettingService

    @Mock
    private lateinit var goldService: GoldService

    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // signup()이 BlindIndexUtil.hash(email)를 호출하므로 EncryptionConfig.instance 필요
        // (단독 실행 시 Spring 컨텍스트가 없어 lateinit 미초기화 방지)
        EncryptionConfig("auth-service-test-key-32-chars!!", "").validate()
        passwordEncoder = BCryptPasswordEncoder()
        authService = AuthService(
            userRepository,
            AccountStatusGuard(jwtTokenProvider),
            org.mockito.kotlin.mock<DevLoginAccessPolicy>(),
            passwordEncoder,
            jwtTokenProvider,
            systemSettingService,
            goldService,
        )
        whenever(systemSettingService.getBoolean("allow_signups", true)).thenReturn(true)
    }

    private fun createTestUser(id: Long, oauthId: String, nickname: String): User {
        return User(
            id = id,
            email = "$oauthId@example.com",
            oauthProvider = "LOCAL",
            oauthId = oauthId,
            username = oauthId,
            password = passwordEncoder.encode("password123"),
            nickname = nickname,
            name = nickname,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )
    }

    @Test
    fun `signup should create user and return auth response`() {
        // Given
        val request = SignupRequest(
            username = "testuser",
            password = "password123",
            nickname = "테스트유저",
            name = "홍길동",
            birthDate = "1990-01-01",
            phoneNumber = "01012345678",
            email = "test@example.com",
            gender = "M",
            birthYear = 1990,
            mainLocationText = "서울시 강남구"
        )

        whenever(userRepository.findByUsername("testuser")).thenReturn(Optional.empty())
        whenever(userRepository.save(any<User>())).thenAnswer { invocation ->
            val user = invocation.getArgument<User>(0)
            User(
                id = 1L,
                email = user.email,
                oauthProvider = user.oauthProvider,
                oauthId = user.oauthId,
                username = user.username,
                password = user.password,
                nickname = user.nickname,
                name = user.name,
                birthDate = user.birthDate,
                phoneNumber = user.phoneNumber,
                gender = user.gender,
                birthYear = user.birthYear,
                mainLocationText = user.mainLocationText,
                mainLocationGeom = user.mainLocationGeom,
                profileImageUrl = user.profileImageUrl
            )
        }
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("test-access-token")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("test-refresh-token")

        // When
        val response = authService.signup(request)

        // Then
        assertNotNull(response)
        assertEquals("test-access-token", response.accessToken)
        assertNotNull(response.user)
        assertEquals("testuser", response.user.username)
        assertEquals("테스트유저", response.user.nickname)
    }

    @Test
    fun `signup should fail when username already exists`() {
        // Given
        val request = SignupRequest(
            username = "existinguser",
            password = "password123",
            nickname = "테스트유저",
            name = "홍길동",
            birthDate = "1990-01-01",
            phoneNumber = "01012345678",
            email = null,
            gender = null,
            birthYear = null,
            mainLocationText = null
        )

        val existingUser = createTestUser(1L, "existinguser", "기존유저")
        whenever(userRepository.findByUsername("existinguser")).thenReturn(Optional.of(existingUser))

        // When & Then
        assertThrows<ConflictException> {
            authService.signup(request)
        }
    }

    @Test
    fun `signup should fail with provider-aware message when email already registered via kakao`() {
        // Given
        val request = SignupRequest(
            username = "newuser",
            password = "password123",
            nickname = "새유저",
            name = "홍길동",
            birthDate = "1990-01-01",
            phoneNumber = "01012345678",
            email = "existing@example.com",
            gender = null,
            birthYear = null,
            mainLocationText = null
        )

        val existingKakaoUser = User(
            id = 41L,
            email = "encrypted",
            oauthProvider = "kakao",
            oauthId = "kakao_abc",
            username = null,
            password = null,
            nickname = "Keasy",
            name = null,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )

        whenever(userRepository.findByUsername("newuser")).thenReturn(Optional.empty())
        whenever(userRepository.findByEmailHash(any())).thenReturn(Optional.of(existingKakaoUser))

        // When & Then
        val ex = assertThrows<ConflictException> { authService.signup(request) }
        assertTrue(ex.message!!.contains("카카오"), "message should hint kakao provider, was: ${ex.message}")
    }

    @Test
    fun `login should succeed with valid credentials`() {
        // Given
        val encodedPassword = passwordEncoder.encode("password123")
        val user = User(
            id = 1L,
            email = "testuser@example.com",
            oauthProvider = "LOCAL",
            oauthId = "LOCAL_testuser",
            username = "testuser",
            password = encodedPassword,
            nickname = "테스트유저",
            name = "테스트유저",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )

        val request = LoginRequest(username = "testuser", password = "password123")

        whenever(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user))
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("test-access-token")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("test-refresh-token")

        // When
        val response = authService.login(request)

        // Then
        assertNotNull(response)
        assertEquals("test-access-token", response.accessToken)
        assertEquals("testuser", response.user.username)
    }

    @Test
    fun `login should fail with invalid username`() {
        // Given
        val request = LoginRequest(username = "nonexistent", password = "password123")
        whenever(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty())

        // When & Then
        assertThrows<UnauthorizedException> {
            authService.login(request)
        }
    }

    @Test
    fun `login should fail with invalid password`() {
        // Given
        val encodedPassword = passwordEncoder.encode("correctpassword")
        val user = User(
            id = 1L,
            email = "testuser@example.com",
            oauthProvider = "LOCAL",
            oauthId = "LOCAL_testuser",
            username = "testuser",
            password = encodedPassword,
            nickname = "테스트유저",
            name = "테스트유저",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )

        val request = LoginRequest(username = "testuser", password = "wrongpassword")
        whenever(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user))

        // When & Then
        assertThrows<UnauthorizedException> {
            authService.login(request)
        }
    }

    @Test
    fun `login should fail when user has no password set`() {
        // Given (OAuth user with no password)
        val user = User(
            id = 1L,
            email = "kakao123@kakao.com",
            oauthProvider = "KAKAO",
            oauthId = "kakao123",
            username = null,
            password = null,
            nickname = "카카오유저",
            name = "카카오유저",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        )

        val request = LoginRequest(username = "kakaouser", password = "password123")
        whenever(userRepository.findByUsername("kakaouser")).thenReturn(Optional.of(user))

        // When & Then
        assertThrows<UnauthorizedException> {
            authService.login(request)
        }
    }

    @Test
    fun `devLogin should return auth response`() {
        // Given
        val user = createTestUser(1L, "devuser", "개발유저")

        whenever(jwtTokenProvider.generateToken(any())).thenReturn("test-access-token")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("test-refresh-token")

        // When
        val response = authService.devLogin(user)

        // Then
        assertNotNull(response)
        assertEquals("test-access-token", response.accessToken)
        assertEquals("devuser", response.user.username)
    }

    // --- signupCompletedAt: 온보딩 완료 단일 기준 ---

    private fun oauthFirstTouchUser(id: Long, signupCompletedAt: LocalDateTime? = null): User =
        User(
            id = id,
            email = "kakao$id@kakao.com",
            oauthProvider = "kakao",
            oauthId = "kakao_$id",
            username = null,
            password = null,
            nickname = "자동닉네임", // OAuth 프로필에서 자동 채워진 값 (완료 신호 아님)
            name = null,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null,
            signupCompletedAt = signupCompletedAt
        )

    @Test
    fun `signup should stamp signupCompletedAt and expose signupCompleted=true`() {
        val request = SignupRequest(
            username = "localuser", password = "password123", nickname = "로컬유저",
            name = "홍길동", birthDate = null, phoneNumber = null, email = null,
            gender = null, birthYear = null, mainLocationText = null
        )
        whenever(userRepository.findByUsername("localuser")).thenReturn(Optional.empty())
        // save 시 signupCompletedAt 보존 (기존 테스트 mock은 드롭함)
        whenever(userRepository.save(any<User>())).thenAnswer { invocation ->
            val u = invocation.getArgument<User>(0)
            User(
                id = 1L, email = u.email, oauthProvider = u.oauthProvider, oauthId = u.oauthId,
                username = u.username, password = u.password, nickname = u.nickname, name = u.name,
                birthDate = u.birthDate, phoneNumber = u.phoneNumber, gender = u.gender,
                birthYear = u.birthYear, mainLocationText = u.mainLocationText,
                mainLocationGeom = u.mainLocationGeom, profileImageUrl = u.profileImageUrl,
                signupCompletedAt = u.signupCompletedAt
            )
        }
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("t")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("r")

        val response = authService.signup(request)

        assertTrue(response.user.signupCompleted, "LOCAL 가입은 즉시 완료여야 한다")
    }

    @Test
    fun `snsSignup should stamp signupCompletedAt when not yet completed`() {
        val user = oauthFirstTouchUser(1L, signupCompletedAt = null)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<User>())).thenAnswer { it.getArgument<User>(0) }
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("t")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("r")

        val request = SnsSignupRequest(
            provider = "kakao", nickname = "닉네임", name = "홍길동",
            birthDate = null, gender = null, phoneNumber = null // 데모그래픽 선택
        )

        val response = authService.snsSignup(1L, request)

        assertNotNull(user.signupCompletedAt, "추가가입 완료 시 timestamp가 set돼야 한다")
        assertTrue(response.user.signupCompleted)
    }

    @Test
    fun `snsSignup should preserve existing signupCompletedAt timestamp`() {
        val fixed = LocalDateTime.of(2025, 1, 1, 0, 0)
        val user = oauthFirstTouchUser(1L, signupCompletedAt = fixed)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<User>())).thenAnswer { it.getArgument<User>(0) }
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("t")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("r")

        val request = SnsSignupRequest(
            provider = "kakao", nickname = "닉네임", name = "홍길동",
            birthDate = null, gender = null, phoneNumber = null
        )

        authService.snsSignup(1L, request)

        assertEquals(fixed, user.signupCompletedAt, "최초 완료 시각은 재호출에도 보존돼야 한다")
    }

    // --- 회원가입 축하 골드: grantWelcomeGoldIfNeeded 호출 검증 ---

    @Test
    fun `signup should call grantWelcomeGoldIfNeeded for the newly created user`() {
        // Given
        val request = SignupRequest(
            username = "welcomeuser", password = "password123", nickname = "웰컴유저",
            name = "홍길동", birthDate = "1990-01-01", phoneNumber = "01012345678",
            email = null, gender = null, birthYear = null, mainLocationText = null
        )
        whenever(userRepository.findByUsername("welcomeuser")).thenReturn(Optional.empty())
        whenever(userRepository.save(any<User>())).thenAnswer { invocation ->
            val u = invocation.getArgument<User>(0)
            User(
                id = 7L, email = u.email, oauthProvider = u.oauthProvider, oauthId = u.oauthId,
                username = u.username, password = u.password, nickname = u.nickname, name = u.name,
                birthDate = u.birthDate, phoneNumber = u.phoneNumber, gender = u.gender,
                birthYear = u.birthYear, mainLocationText = u.mainLocationText,
                mainLocationGeom = u.mainLocationGeom, profileImageUrl = u.profileImageUrl
            )
        }
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("t")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("r")

        // When
        authService.signup(request)

        // Then
        verify(goldService).grantWelcomeGoldIfNeeded(7L)
    }

    @Test
    fun `snsSignup should call grantWelcomeGoldIfNeeded on first onboarding completion`() {
        // Given
        val user = oauthFirstTouchUser(1L, signupCompletedAt = null)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<User>())).thenAnswer { it.getArgument<User>(0) }
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("t")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("r")

        val request = SnsSignupRequest(
            provider = "kakao", nickname = "닉네임", name = "홍길동",
            birthDate = null, gender = null, phoneNumber = null
        )

        // When
        authService.snsSignup(1L, request)

        // Then
        verify(goldService).grantWelcomeGoldIfNeeded(1L)
    }

    @Test
    fun `snsSignup should not call grantWelcomeGoldIfNeeded when already completed`() {
        // Given: 이미 온보딩 완료된 사용자가 snsSignup을 재호출하는 경우 (AuthService.kt:183 firstCompletion 가드)
        val fixed = LocalDateTime.of(2025, 1, 1, 0, 0)
        val user = oauthFirstTouchUser(1L, signupCompletedAt = fixed)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<User>())).thenAnswer { it.getArgument<User>(0) }
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("t")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("r")

        val request = SnsSignupRequest(
            provider = "kakao", nickname = "닉네임", name = "홍길동",
            birthDate = null, gender = null, phoneNumber = null
        )

        // When
        authService.snsSignup(1L, request)

        // Then
        verify(goldService, never()).grantWelcomeGoldIfNeeded(any())
    }

    @Test
    fun `login should expose signupCompleted from signupCompletedAt`() {
        val completed = createTestUser(1L, "completeduser", "완료유저")
            .apply { signupCompletedAt = LocalDateTime.now() }
        whenever(userRepository.findByUsername("completeduser")).thenReturn(Optional.of(completed))
        whenever(jwtTokenProvider.generateToken(any())).thenReturn("t")
        whenever(jwtTokenProvider.generateRefreshToken(any())).thenReturn("r")

        val response = authService.login(LoginRequest("completeduser", "password123"))

        assertTrue(response.user.signupCompleted)
    }
}
