package com.goldpet.service.auth

import com.goldpet.domain.common.util.ExternalJson
import com.goldpet.domain.common.util.ExternalJson.objectListAtOrNull
import com.goldpet.domain.common.util.ExternalJson.idAt
import com.goldpet.domain.common.util.ExternalJson.objectAt
import com.goldpet.domain.common.util.ExternalJson.objectAtOrNull
import com.goldpet.domain.common.util.ExternalJson.stringAt
import com.goldpet.domain.common.util.ExternalJson.stringAtOrNull
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.auth.dto.AuthResponse
import com.goldpet.domain.auth.dto.LinkSuggestionInfo
import com.goldpet.domain.auth.entity.UserAuthProvider
import com.goldpet.domain.auth.exception.AccountStatusException
import com.goldpet.domain.auth.repository.UserAuthProviderRepository
import com.goldpet.domain.auth.service.OAuthNonceService
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserProfileImage
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.common.util.toHttps
import java.time.LocalDateTime
import java.util.Base64
import java.util.Date
import javax.crypto.SecretKey

@Service
class SocialLoginService(
    // STYLE-001: 계정 상태 검사를 공용 가드로 통일(휴면 해제 정보 동봉).
    private val accountStatusGuard: com.goldpet.domain.auth.service.AccountStatusGuard,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val userAuthProviderRepository: UserAuthProviderRepository,
    private val systemSettingService: SystemSettingService,
    private val oauthNonceService: OAuthNonceService,
    @Value("\${app.jwt.secret}")
    private val jwtSecret: String
) {
    private val restTemplate = RestTemplate()
    private val linkSuggestionTtlMs = 5 * 60 * 1000L // 5 minutes

    private val tempKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    // --- Kakao ---

    @Transactional
    fun loginWithKakao(accessToken: String): AuthResponse {
        val headers = HttpHeaders()
        headers.setBearerAuth(accessToken)
        val entity = HttpEntity<String>(headers)

        val response = restTemplate.exchange(
            "https://kapi.kakao.com/v2/user/me",
            HttpMethod.GET,
            entity,
            Map::class.java
        )

        // STYLE-002: 언체크드 캐스트 대신 안전 파서. 응답 형태가 바뀌면 원인이 담긴 예외가 난다.
        val attributes = ExternalJson.asObject(response.body, "kakao")
        val kakaoId = attributes.idAt("id", "kakao")

        val account = attributes.objectAtOrNull("kakao_account")
        val profile = account?.objectAtOrNull("profile")
        val props = attributes.objectAtOrNull("properties")

        val email = account?.stringAtOrNull("email")
        val nickname = profile?.stringAtOrNull("nickname")
            ?: props?.stringAtOrNull("nickname")
        val profileImage = (profile?.stringAtOrNull("profile_image_url")
            ?: props?.stringAtOrNull("profile_image")
            ?: account?.stringAtOrNull("profile_image_url")).toHttps()

        return handleOAuthLogin(
            provider = "kakao",
            oauthId = kakaoId,
            email = email,
            nickname = nickname,
            profileImage = profileImage,
            attributes = attributes,
            skipEmailMatch = false
        )
    }

    // --- Naver ---

    @Transactional
    fun loginWithNaver(accessToken: String): AuthResponse {
        val headers = HttpHeaders()
        headers.setBearerAuth(accessToken)
        val entity = HttpEntity<String>(headers)

        val response = restTemplate.exchange(
            "https://openapi.naver.com/v1/nid/me",
            HttpMethod.GET,
            entity,
            Map::class.java
        )

        val attributes = ExternalJson.asObject(response.body, "naver")
        val resp = attributes.objectAt("response", "naver")

        val naverId = resp.idAt("id", "naver")
        val email = resp.stringAtOrNull("email")
        val nickname = resp.stringAtOrNull("name")
        val profileImage = resp.stringAtOrNull("profile_image").toHttps()

        return handleOAuthLogin(
            provider = "naver",
            oauthId = naverId,
            email = email,
            nickname = nickname,
            profileImage = profileImage,
            attributes = attributes,
            skipEmailMatch = false
        )
    }

    // --- Google ---

    @Transactional
    fun loginWithGoogle(accessToken: String): AuthResponse {
        val url = "https://oauth2.googleapis.com/tokeninfo?id_token=$accessToken"
        val response = restTemplate.getForObject(url, Map::class.java)
        val attributes = ExternalJson.asObject(response, "google")

        val googleId = attributes.idAt("sub", "google")
        val email = attributes.stringAtOrNull("email")
        val nickname = attributes.stringAtOrNull("name")
        val profileImage = attributes.stringAtOrNull("picture").toHttps()

        return handleOAuthLogin(
            provider = "google",
            oauthId = googleId,
            email = email,
            nickname = nickname,
            profileImage = profileImage,
            attributes = attributes,
            skipEmailMatch = false
        )
    }

    // --- Apple ---

    @Transactional
    fun loginWithApple(identityToken: String): AuthResponse {
        val appleKeysResponse = restTemplate.getForObject(
            "https://appleid.apple.com/auth/keys",
            Map::class.java
        )
        val keys = ExternalJson.asObject(appleKeysResponse, "apple").objectListAtOrNull("keys")
            ?: throw BadRequestException("Failed to get Apple public keys")

        val parts = identityToken.split(".")
        if (parts.size < 2) throw BadRequestException("Invalid Identity Token format")

        val headerJson = String(Decoders.BASE64URL.decode(parts[0]))
        val header = ExternalJson.asObject(ObjectMapper().readValue(headerJson, Map::class.java), "apple")
        val kid = header.stringAt("kid", "apple")

        val keyMap = keys.find { it["kid"] == kid }
            ?: throw BadRequestException("Invalid key identifier")

        val n = BigInteger(1, Decoders.BASE64URL.decode(keyMap["n"] as String))
        val e = BigInteger(1, Decoders.BASE64URL.decode(keyMap["e"] as String))
        val publicKeySpec = RSAPublicKeySpec(n, e)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        val claims = Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(identityToken)
            .payload

        val appleId = claims.subject
        val email = claims["email"] as? String

        val attributes = mutableMapOf<String, Any>("sub" to appleId).apply {
            if (!email.isNullOrBlank()) put("email", email)
        }

        // Apple private relay emails skip email matching
        val skipEmailMatch = email?.contains("@privaterelay.appleid.com") == true

        return handleOAuthLogin(
            provider = "apple",
            oauthId = appleId,
            email = email,
            nickname = null,
            profileImage = null,
            attributes = attributes,
            skipEmailMatch = skipEmailMatch
        )
    }

    // --- Core logic ---

    // internal(테스트 가시성): 기존 소셜 계정 로그인 경로의 signupCompleted 노출 검증용.
    internal fun handleOAuthLogin(
        provider: String,
        oauthId: String,
        email: String?,
        nickname: String?,
        profileImage: String?,
        attributes: Map<String, Any?>,
        skipEmailMatch: Boolean
    ): AuthResponse {
        val linkSuggestionEnabled = systemSettingService.getBoolean("auth.link_suggestion.enabled", false)

        if (!linkSuggestionEnabled) {
            // Original auto-link behavior
            return legacyLogin(provider, oauthId, email, nickname, profileImage, attributes)
        }

        // 1. Check by provider + oauthId in user_auth_providers table
        val existingProvider = userAuthProviderRepository.findByProviderAndProviderId(provider, oauthId).orElse(null)
        if (existingProvider != null) {
            val user = existingProvider.user
            accountStatusGuard.check(user)
            user.lastLoginAt = java.time.LocalDateTime.now()
            // Also sync legacy users table fields
            syncUserFields(user, provider, oauthId, email, nickname, profileImage)
            val token = generateJwt(user, attributes)
            val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)
            return AuthResponse(
                accessToken = token,
                refreshToken = refreshToken,
                user = AuthResponse.UserInfo(
                    id = user.id,
                    username = user.username ?: user.nickname ?: "User",
                    nickname = user.nickname ?: "User",
                    email = user.email,
                    phoneNumber = user.phoneNumber,
                    name = user.name,
                    signupCompleted = user.signupCompletedAt != null
                )
            )
        }

        // 2. Email match check
        val emailMatchedUser = if (!skipEmailMatch && !email.isNullOrBlank()) {
            userRepository.findByEmailHash(BlindIndexUtil.hash(email)!!).orElse(null)
        } else null

        if (emailMatchedUser != null) {
            // Email matched a different provider — suggest linking instead of auto-linking
            val existingProviders = userAuthProviderRepository.findAllByUserId(emailMatchedUser.id)
            val existingProviderName = existingProviders.firstOrNull()?.provider
                ?: emailMatchedUser.oauthProvider.ifBlank { "unknown" }

            val tempToken = generateLinkSuggestionToken(emailMatchedUser.id, provider, oauthId)
            return AuthResponse(
                accessToken = "",
                refreshToken = "",
                user = AuthResponse.UserInfo(
                    id = 0,
                    username = "",
                    nickname = "",
                    email = null
                ),
                linkSuggestion = LinkSuggestionInfo(
                    maskedEmail = maskEmail(email!!),
                    existingProvider = existingProviderName,
                    tempToken = tempToken
                )
            )
        }

        // 3. No match — create new user + dual-write UserAuthProvider
        var user = User(
            oauthProvider = provider,
            oauthId = oauthId,
            email = email,
            emailHash = BlindIndexUtil.hash(email),
            nickname = nickname ?: if (provider == "apple") "Apple User" else null,
            profileImageUrl = profileImage,
            username = null,
            password = null,
            gender = null,
            birthYear = null,
            mainLocationText = null,
            mainLocationGeom = null,
            name = null,
            birthDate = null,
            phoneNumber = null
        )
        user = userRepository.save(user)

        if (user.profileImageUrl != null) {
            user.profileImages.add(UserProfileImage(user = user, imageUrl = user.profileImageUrl!!, orderIndex = 0))
            userRepository.save(user)
        }

        userAuthProviderRepository.save(
            UserAuthProvider(
                user = user,
                provider = provider,
                providerId = oauthId,
                isPrimary = true
            )
        )

        accountStatusGuard.check(user)
            user.lastLoginAt = java.time.LocalDateTime.now()
        val token = generateJwt(user, attributes)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        return AuthResponse(
            accessToken = token,
            refreshToken = refreshToken,
            user = AuthResponse.UserInfo(
                id = user.id,
                username = user.username ?: user.nickname ?: "User",
                nickname = user.nickname ?: "User",
                email = user.email,
                phoneNumber = user.phoneNumber,
                name = user.name,
                signupCompleted = user.signupCompletedAt != null
            )
        )
    }

    // Original behavior (flag OFF)
    private fun legacyLogin(
        provider: String,
        oauthId: String,
        email: String?,
        nickname: String?,
        profileImage: String?,
        attributes: Map<String, Any?>
    ): AuthResponse {
        var user = userRepository.findByOauthProviderAndOauthId(provider, oauthId).orElse(null)
        if (user == null && !email.isNullOrBlank()) {
            user = userRepository.findByEmailHash(BlindIndexUtil.hash(email)!!).orElse(null)
        }
        if (user == null) {
            user = User(
                oauthProvider = provider,
                oauthId = oauthId,
                email = email,
                emailHash = BlindIndexUtil.hash(email),
                nickname = nickname ?: if (provider == "apple") "Apple User" else null,
                profileImageUrl = profileImage,
                username = null,
                password = null,
                gender = null,
                birthYear = null,
                mainLocationText = null,
                mainLocationGeom = null,
                name = null,
                birthDate = null,
                phoneNumber = null
            )
            user = userRepository.save(user)
            if (user.profileImageUrl != null) {
                user.profileImages.add(UserProfileImage(user = user, imageUrl = user.profileImageUrl!!, orderIndex = 0))
                userRepository.save(user)
            }
        } else {
            syncUserFields(user, provider, oauthId, email, nickname, profileImage)
        }

        accountStatusGuard.check(user)
            user.lastLoginAt = java.time.LocalDateTime.now()
        val token = generateJwt(user, attributes)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        return AuthResponse(
            accessToken = token,
            refreshToken = refreshToken,
            user = AuthResponse.UserInfo(
                id = user.id,
                username = user.username ?: user.nickname ?: "User",
                nickname = user.nickname ?: "User",
                email = user.email,
                phoneNumber = user.phoneNumber,
                name = user.name,
                signupCompleted = user.signupCompletedAt != null
            )
        )
    }

    private fun syncUserFields(user: User, provider: String, oauthId: String, email: String?, nickname: String?, profileImage: String?) {
        var changed = false
        if (!email.isNullOrBlank() && user.email != email) { user.email = email; user.emailHash = BlindIndexUtil.hash(email); changed = true }
        if (nickname != null && user.nickname == null) { user.nickname = nickname; changed = true }
        if (profileImage != null && user.profileImages.isEmpty() && user.profileImageUrl != profileImage) {
            user.profileImageUrl = profileImage
            user.profileImages.add(UserProfileImage(user = user, imageUrl = profileImage, orderIndex = 0))
            changed = true
        }
        if (user.oauthProvider.isBlank()) { user.oauthProvider = provider; user.oauthId = oauthId; changed = true }
        if (changed) userRepository.save(user)
    }

    // --- Confirm link (called by AuthController) ---

    @Transactional
    fun confirmLink(tempToken: String): AuthResponse {
        val claims = try {
            Jwts.parser()
                .verifyWith(tempKey)
                .build()
                .parseSignedClaims(tempToken)
                .payload
        } catch (ex: Exception) {
            throw BadRequestException("Invalid or expired link token")
        }

        if (claims["type"] != "LINK_SUGGESTION") {
            throw BadRequestException("Invalid token type")
        }

        // V67: nonce atomic consume — 1회용 검증. pre-V67 token (nonce 없음) 도 거부 (replay 차단 우선).
        val nonceClaim = claims["nonce"] as? String
            ?: throw BadRequestException("Link token missing nonce — re-initiate link flow")
        if (!oauthNonceService.consume(nonceClaim)) {
            throw BadRequestException("Link token already used or expired — replay rejected")
        }

        val userId = claims.subject.toLong()
        val newProvider = claims["newProvider"] as String
        val newProviderId = claims["newProviderId"] as String

        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found") }

        // Link the new provider (idempotent)
        val alreadyLinked = userAuthProviderRepository.findByProviderAndProviderId(newProvider, newProviderId).isPresent
        if (!alreadyLinked) {
            userAuthProviderRepository.save(
                UserAuthProvider(
                    user = user,
                    provider = newProvider,
                    providerId = newProviderId,
                    isPrimary = false
                )
            )
        }

        accountStatusGuard.check(user)
            user.lastLoginAt = java.time.LocalDateTime.now()
        val attributes = mapOf<String, Any>()
        val token = generateJwt(user, attributes)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        return AuthResponse(
            accessToken = token,
            refreshToken = refreshToken,
            user = AuthResponse.UserInfo(
                id = user.id,
                username = user.username ?: user.nickname ?: "User",
                nickname = user.nickname ?: "User",
                email = user.email,
                phoneNumber = user.phoneNumber,
                name = user.name,
                signupCompleted = user.signupCompletedAt != null
            )
        )
    }

    // --- Helpers ---


    private fun generateJwt(user: User, attributes: Map<String, Any?>): String {
        // UserPrincipal 은 값이 null 이 아닌 속성만 받는다(OAuth2User 계약).
        val userPrincipal = UserPrincipal.create(
            user,
            attributes.filterValues { it != null }.mapValues { it.value!! }
        )
        val authentication = UsernamePasswordAuthenticationToken(
            userPrincipal,
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        return jwtTokenProvider.generateToken(authentication)
    }

    private fun generateLinkSuggestionToken(userId: Long, newProvider: String, newProviderId: String): String {
        // V67: nonce 발급 — confirmLink 시 1회용 검증으로 replay 차단.
        val nonce = oauthNonceService.issue(userId, "LINK_SUGGESTION", ttlSeconds = linkSuggestionTtlMs / 1000)
        val now = Date()
        val expiry = Date(now.time + linkSuggestionTtlMs)
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "LINK_SUGGESTION")
            .claim("newProvider", newProvider)
            .claim("newProviderId", newProviderId)
            .claim("nonce", nonce.toString())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(tempKey)
            .compact()
    }

    private fun maskEmail(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex <= 0) return "***"
        val local = email.substring(0, atIndex)
        val domain = email.substring(atIndex)
        val masked = if (local.length <= 1) "*" else local[0] + "***"
        return "$masked$domain"
    }
}
