package com.goldpet.domain.auth.integration

import com.goldpet.IntegrationTestBase
import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.auth.entity.UserAuthProvider
import com.goldpet.domain.auth.repository.UserAuthProviderRepository
import com.goldpet.domain.auth.service.OAuthNonceService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.service.auth.SocialLoginService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.util.Date
import java.util.UUID

/**
 * OAuth 계정 연동(Account Linking) 통합 테스트 — AUTH-10 시나리오.
 *
 * 시나리오:
 *   1. KAKAO 유저 생성 + UserAuthProvider(KAKAO, isPrimary=true) 등록
 *   2. LINK_SUGGESTION JWT 수동 생성
 *      (SocialLoginService.generateLinkSuggestionToken 과 동일 알고리즘 + app.jwt.secret 키 사용)
 *   3. SocialLoginService.confirmLink(tempToken) 호출
 *   4. UserAuthProviderRepository 에 2건(KAKAO + GOOGLE)이 존재하고
 *      isPrimary=true 가 정확히 1건임을 검증
 */
@Tag("integration")
class AccountLinkingIT : IntegrationTestBase() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var userAuthProviderRepository: UserAuthProviderRepository
    @Autowired private lateinit var socialLoginService: SocialLoginService
    @Autowired private lateinit var oauthNonceService: OAuthNonceService

    /**
     * SocialLoginService 가 link-suggestion JWT 서명에 사용하는 키.
     * application.yml: app.jwt.secret = ${JWT_SECRET:...}
     * application-test.yml 은 이 키를 오버라이드하지 않으므로 main yml 기본값을 사용.
     */
    @Value("\${app.jwt.secret}")
    private lateinit var appJwtSecret: String

    // PER_METHOD 라이프사이클: 반복마다 새 인스턴스 → uid 도 매번 새로 생성됨
    private val uid: String = UUID.randomUUID().toString().replace("-", "").take(10)

    private lateinit var kakaoUser: User

    @BeforeEach
    fun setUp() {
        kakaoUser = userRepository.save(
            User(
                id = 0,
                email = "link_$uid@goldpet.com",
                emailHash = BlindIndexUtil.hash("link_$uid@goldpet.com"),
                oauthProvider = "kakao",
                oauthId = "kakao_$uid",
                username = null,
                password = null,
                nickname = "LinkUser_$uid",
                name = null,
                birthDate = null,
                phoneNumber = null,
                gender = null,
                birthYear = null,
                mainLocationText = null,
                mainLocationGeom = null,
                profileImageUrl = null
            )
        )
        userAuthProviderRepository.save(
            UserAuthProvider(
                user = kakaoUser,
                provider = "kakao",
                providerId = "kakao_$uid",
                isPrimary = true
            )
        )
    }

    @AfterEach
    fun tearDown() {
        runCatching { userAuthProviderRepository.deleteAllByUserId(kakaoUser.id) }
        runCatching { userRepository.delete(kakaoUser) }
    }

    /**
     * confirmLink 가 두 번째 provider 를 저장하고 유효한 accessToken 을 반환함을 5회 반복 검증한다.
     *
     * 검증 항목:
     *  - authResponse.accessToken 이 빈 문자열이 아님
     *  - authResponse.user.id == kakaoUser.id (primary user 유지)
     *  - UserAuthProvider 2건 (kakao + google)
     *  - isPrimary=true 가 정확히 1건 (KAKAO 가 primary 를 유지)
     *  - 새로 연결된 google provider 의 isPrimary == false
     */
    @RepeatedTest(5)
    fun `confirmLink adds second provider and returns valid auth - 2 providers, exactly 1 primary`() {
        // 반복마다 고유 providerId 사용 (중복 충돌 방지)
        val googleProviderId = "google_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        // V67: confirmLink 는 1회용 nonce 를 요구한다 — production 과 동일하게 발급해 claim 에 포함.
        val nonce = oauthNonceService.issue(kakaoUser.id, "LINK_SUGGESTION", ttlSeconds = 300)

        // SocialLoginService.generateLinkSuggestionToken 과 동일한 알고리즘으로 JWT 생성
        val key = Keys.hmacShaKeyFor(appJwtSecret.toByteArray())
        val now = Date()
        val tempToken = Jwts.builder()
            .subject(kakaoUser.id.toString())
            .claim("type", "LINK_SUGGESTION")
            .claim("newProvider", "google")
            .claim("newProviderId", googleProviderId)
            .claim("nonce", nonce.toString())
            .issuedAt(now)
            .expiration(Date(now.time + 5 * 60 * 1_000L))
            .signWith(key)
            .compact()

        val authResponse = socialLoginService.confirmLink(tempToken)

        // 유효한 JWT 와 올바른 userId 반환
        assertThat(authResponse.accessToken).isNotBlank()
        assertThat(authResponse.user.id).isEqualTo(kakaoUser.id)

        // provider 2건 존재 확인
        val providers = userAuthProviderRepository.findAllByUserId(kakaoUser.id)
        assertThat(providers)
            .withFailMessage("KAKAO + GOOGLE 2건이어야 합니다. 실제: ${providers.map { it.provider }}")
            .hasSize(2)
        assertThat(providers.map { it.provider })
            .containsExactlyInAnyOrder("kakao", "google")

        // primary 는 정확히 1개 (기존 KAKAO)
        assertThat(providers.count { it.isPrimary })
            .withFailMessage("isPrimary=true 가 정확히 1건이어야 합니다")
            .isEqualTo(1)
        assertThat(providers.first { it.provider == "google" }.isPrimary).isFalse()
    }
}
