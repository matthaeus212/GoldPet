package com.goldpet.domain.user.service

import com.goldpet.domain.user.dto.ProfileImageUpdateRequest
import com.goldpet.domain.user.dto.UserProfileUpdateRequest
import com.goldpet.domain.user.entity.Hobby
import com.goldpet.domain.user.entity.Interest
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.DeletedUserRepository
import com.goldpet.domain.user.repository.HobbyRepository
import com.goldpet.domain.user.repository.InterestRepository
import com.goldpet.domain.user.repository.UserDeviceRepository
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.auth.repository.UserAuthProviderRepository
import com.goldpet.domain.friend.repository.MatchRepository
import com.goldpet.domain.friend.repository.LikeRepository
import com.goldpet.domain.common.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.*

class UserServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var deletedUserRepository: DeletedUserRepository

    @Mock
    private lateinit var userDeviceRepository: UserDeviceRepository

    @Mock
    private lateinit var userAuthProviderRepository: UserAuthProviderRepository

    @Mock
    private lateinit var interestRepository: InterestRepository

    @Mock
    private lateinit var hobbyRepository: HobbyRepository

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var matchRepository: MatchRepository

    @Mock
    private lateinit var likeRepository: LikeRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var userService: UserService

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        userService = UserService(userRepository, deletedUserRepository, userDeviceRepository, userAuthProviderRepository, interestRepository, hobbyRepository, eventPublisher, matchRepository, likeRepository, passwordEncoder)

        testUser = createTestUser(1L, "testuser", "테스트유저")
    }

    private fun createTestUser(id: Long, oauthId: String, nickname: String): User {
        return User(
            id = id,
            email = "$oauthId@example.com",
            oauthProvider = "LOCAL",
            oauthId = oauthId,
            username = oauthId,
            password = "password",
            nickname = nickname,
            name = nickname,
            birthDate = null,
            phoneNumber = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            profileImageUrl = null
        ).apply {
            createdAt = LocalDateTime.now()
            updatedAt = LocalDateTime.now()
        }
    }

    @Test
    fun `findUserById should return user when exists`() {
        // Given
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))

        // When
        val result = userService.findUserById(1L)

        // Then
        assertNotNull(result)
        assertEquals("testuser", result?.username)
        assertEquals("테스트유저", result?.nickname)
    }

    @Test
    fun `findUserById should return null when user does not exist`() {
        // Given
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        // When
        val result = userService.findUserById(999L)

        // Then
        assertNull(result)
    }

    @Test
    fun `findUserByUsername should return user when exists`() {
        // Given
        whenever(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser))

        // When
        val result = userService.findUserByUsername("testuser")

        // Then
        assertNotNull(result)
        assertEquals("testuser", result?.username)
    }

    @Test
    fun `createUser should create new OAuth user`() {
        // Given
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
            ).apply {
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }
        }

        // When
        val result = userService.createUser("KAKAO", "kakao123", "카카오유저")

        // Then
        assertNotNull(result)
        assertEquals("KAKAO", result.oauthProvider)
        assertEquals("kakao123", result.oauthId)
        assertEquals("카카오유저", result.nickname)
    }

    @Test
    fun `updateNotification should toggle notification setting`() {
        // Given
        testUser.isNotificationEnabled = false
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)

        // When
        val result = userService.updateNotification(1L, true)

        // Then
        assertTrue(result.isNotificationEnabled)
    }

    @Test
    fun `updateNotification should throw exception when user not found`() {
        // Given
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            userService.updateNotification(999L, true)
        }
    }

    @Test
    fun `updateUserProfile should update profile fields`() {
        // Given
        val request = UserProfileUpdateRequest(
            name = "홍길동",
            nickname = "새닉네임",
            birthDate = "1990-01-01",
            phoneNumber = "01012345678",
            gender = "M",
            hasPet = true,
            intro = "안녕하세요",
            mbti = "ENFP",
            interests = listOf("산책", "맛집탐방"),
            hobbies = listOf("독서", "영화감상")
        )

        val interest1 = Interest(id = 1L, name = "산책", orderIndex = 1)
        val interest2 = Interest(id = 2L, name = "맛집탐방", orderIndex = 2)
        val hobby1 = Hobby(id = 1L, name = "독서", orderIndex = 1)
        val hobby2 = Hobby(id = 2L, name = "영화감상", orderIndex = 2)

        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(interestRepository.findByNameIn(listOf("산책", "맛집탐방"))).thenReturn(listOf(interest1, interest2))
        whenever(hobbyRepository.findByNameIn(listOf("독서", "영화감상"))).thenReturn(listOf(hobby1, hobby2))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)

        // When
        val result = userService.updateUserProfile(1L, request)

        // Then
        assertEquals("홍길동", result.name)
        assertEquals("새닉네임", result.nickname)
        assertEquals("M", result.gender)
        assertTrue(result.hasPet)
        assertEquals("안녕하세요", result.intro)
        assertEquals("ENFP", result.mbti)
    }

    @Test
    fun `updateUserProfile should throw exception when user not found`() {
        // Given
        val request = UserProfileUpdateRequest(
            name = null,
            nickname = "새닉네임",
            birthDate = null,
            phoneNumber = null,
            gender = null,
            hasPet = null,
            intro = null,
            mbti = null
        )
        whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<NotFoundException> {
            userService.updateUserProfile(999L, request)
        }
    }

    @Test
    fun `getAllInterests should return ordered list`() {
        // Given
        val interests = listOf(
            Interest(id = 1L, name = "산책", orderIndex = 1),
            Interest(id = 2L, name = "맛집탐방", orderIndex = 2),
            Interest(id = 3L, name = "여행", orderIndex = 3)
        )
        whenever(interestRepository.findAllByOrderByOrderIndexAsc()).thenReturn(interests)

        // When
        val result = userService.getAllInterests()

        // Then
        assertEquals(3, result.size)
        assertEquals("산책", result[0].name)
        assertEquals("맛집탐방", result[1].name)
        assertEquals("여행", result[2].name)
    }

    @Test
    fun `getAllHobbies should return ordered list`() {
        // Given
        val hobbies = listOf(
            Hobby(id = 1L, name = "독서", orderIndex = 1),
            Hobby(id = 2L, name = "영화감상", orderIndex = 2)
        )
        whenever(hobbyRepository.findAllByOrderByOrderIndexAsc()).thenReturn(hobbies)

        // When
        val result = userService.getAllHobbies()

        // Then
        assertEquals(2, result.size)
        assertEquals("독서", result[0].name)
        assertEquals("영화감상", result[1].name)
    }

    @Test
    fun `updateUserProfileImages should update single image`() {
        // Given
        val request = ProfileImageUpdateRequest(
            profileImageUrl = "https://example.com/image.jpg",
            profileImageUrls = null
        )
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)

        // When
        val result = userService.updateUserProfileImages(1L, request)

        // Then
        assertEquals("https://example.com/image.jpg", result.profileImageUrl)
    }

    // Regression guard for profile-image silent-fail bug.
    // 클라이언트가 profileImageUrl 만 보내면 DTO 의 profileImageUrls default 가 null 이어야
    // 두 번째 블록이 스킵되고 단일 url 이 보존된다. default 가 emptyList() 였을 때는
    // clear() + firstOrNull() = null 로 덮어써져 HTTP 200 인데 DB 가 null 로 비는 silent fail 이 났다.
    @Test
    fun `updateUserProfileImages should persist single url when only profileImageUrl is set (DTO default)`() {
        // Given — 실제 클라이언트가 profileImageUrl 만 보내는 시나리오 (DTO default 의존)
        val request = ProfileImageUpdateRequest(profileImageUrl = "https://example.com/single.jpg")
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenAnswer { it.getArgument<User>(0) }

        // When
        val result = userService.updateUserProfileImages(1L, request)

        // Then — DTO default 가 null 이므로 두 번째 블록이 진입하지 않아야 단일 url 이 보존됨
        assertEquals("https://example.com/single.jpg", result.profileImageUrl)
        assertEquals(1, result.profileImages.size)
        assertEquals("https://example.com/single.jpg", result.profileImages[0].imageUrl)
    }

    // Regression guard: 명시적으로 빈 리스트를 보내도 단일 url 이 보존되어야 한다
    // (service 의 isNotEmpty 가드 동작 검증)
    @Test
    fun `updateUserProfileImages should preserve single url when profileImageUrls is explicit empty list`() {
        // Given
        val request = ProfileImageUpdateRequest(
            profileImageUrl = "https://example.com/keep.jpg",
            profileImageUrls = emptyList()
        )
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenAnswer { it.getArgument<User>(0) }

        // When
        val result = userService.updateUserProfileImages(1L, request)

        // Then — 빈 리스트는 takeIf 가드로 스킵되어 단일 url 보존
        assertEquals("https://example.com/keep.jpg", result.profileImageUrl)
        assertEquals(1, result.profileImages.size)
    }

    @Test
    fun `updateUserProfileImages should update multiple images`() {
        // Given
        val request = ProfileImageUpdateRequest(
            profileImageUrl = null,
            profileImageUrls = listOf(
                "https://example.com/image1.jpg",
                "https://example.com/image2.jpg",
                "https://example.com/image3.jpg"
            )
        )
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(testUser))
        whenever(userRepository.save(any<User>())).thenReturn(testUser)

        // When
        val result = userService.updateUserProfileImages(1L, request)

        // Then
        assertEquals("https://example.com/image1.jpg", result.profileImageUrl)
        assertEquals(3, result.profileImages.size)
    }

    @Test
    fun `isNicknameAvailable should return true when nickname is not taken`() {
        // Given
        whenever(userRepository.findByNickname("새닉네임")).thenReturn(Optional.empty())

        // When
        val result = userService.isNicknameAvailable("새닉네임")

        // Then
        assertTrue(result)
    }

    @Test
    fun `isNicknameAvailable should return false when nickname is taken`() {
        // Given
        whenever(userRepository.findByNickname("기존닉네임")).thenReturn(Optional.of(testUser))

        // When
        val result = userService.isNicknameAvailable("기존닉네임")

        // Then
        assertFalse(result)
    }
}
