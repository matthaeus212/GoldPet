package com.goldpet.service.oauth2

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserProfileImage
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.common.util.toHttps
import com.nimbusds.jwt.JWTParser
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomOAuth2UserService(private val userRepository: UserRepository) :
        DefaultOAuth2UserService() {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val registrationId = userRequest.clientRegistration.registrationId
        val userNameAttributeName =
                userRequest
                        .clientRegistration
                        .providerDetails
                        .userInfoEndpoint
                        .userNameAttributeName

        val oAuth2User: OAuth2User
        val oauth2Id: String

        if (registrationId == "apple") {
            // Apple does not support the UserInfo endpoint. We must parse the id_token.
            val idToken =
                    userRequest.additionalParameters["id_token"] as? String
                            ?: throw IllegalStateException("No id_token found for Apple login")

            try {
                val jwt = JWTParser.parse(idToken)
                val claims = jwt.jwtClaimsSet.claims

                // Apple's unique user ID is in the 'sub' claim
                oauth2Id =
                        claims["sub"]?.toString()
                                ?: throw IllegalStateException("No 'sub' claim in Apple id_token")

                oAuth2User =
                        DefaultOAuth2User(
                                listOf(SimpleGrantedAuthority("ROLE_USER")),
                                claims,
                                "sub" // Apple uses 'sub' as the name attribute
                        )
            } catch (e: Exception) {
                throw IllegalStateException("Failed to parse Apple id_token", e)
            }
        } else {
            // For other providers (Naver, Kakao, Google), use the default loading (UserInfo
            // endpoint)
            oAuth2User = super.loadUser(userRequest)

            // Extract the provider‑specific OAuth2 ID
            oauth2Id =
                    when (registrationId) {
                        "naver" -> {
                            val resp = oAuth2User.attributes["response"] as? Map<*, *>
                            resp?.get("id")?.toString()
                        }
                        "kakao" -> {
                            oAuth2User.attributes["id"]?.toString()
                        }
                        else -> {
                            oAuth2User.attributes[userNameAttributeName]?.toString()
                        }
                    }
                            ?: throw IllegalStateException("OAuth2 ID not found")
        }

        // ---------- provider‑specific attribute extraction ----------
        val (email, nickname, profileImage) =
                when (registrationId) {
                    "naver" -> {
                        val resp = oAuth2User.attributes["response"] as? Map<*, *>
                        Triple(
                                resp?.get("email")?.toString(),
                                resp?.get("name")?.toString(),
                                resp?.get("profile_image")?.toString().toHttps()
                        )
                    }
                    "kakao" -> {
                        // Kakao structure:
                        // id (top level)
                        // kakao_account { email, profile { nickname, profile_image_url } }
                        // properties { nickname, profile_image, ... } (sometimes used)

                        val account = oAuth2User.attributes["kakao_account"] as? Map<*, *>
                        val profile = account?.get("profile") as? Map<*, *>
                        val props = oAuth2User.attributes["properties"] as? Map<*, *>

                        val kakaoEmail = account?.get("email")?.toString()
                        val kakaoNickname =
                                profile?.get("nickname")?.toString()
                                        ?: props?.get("nickname")?.toString()
                        val kakaoImage =
                                (profile?.get("profile_image_url")?.toString()
                                        ?: props?.get("profile_image")?.toString()
                                                ?: account?.get("profile_image_url")
                                                ?.toString()).toHttps() // Fallback

                        Triple(kakaoEmail, kakaoNickname, kakaoImage)
                    }
                    "google" -> {
                        Triple(
                                oAuth2User.attributes["email"]?.toString(),
                                oAuth2User.attributes["name"]?.toString(),
                                oAuth2User.attributes["picture"]?.toString().toHttps()
                        )
                    }
                    "apple" -> {
                        // Apple provides email in the id_token.
                        // Name is ONLY provided in the 'user' field of the authorization response
                        // (POST body) on the FIRST login.
                        // It is NOT in the id_token. Handling that requires a custom filter or
                        // interceptor.
                        // Here we extract what we can from the id_token.
                        Triple(
                                oAuth2User.attributes["email"]?.toString(),
                                null, // Name not available in id_token
                                null // No profile image
                        )
                    }
                    else -> {
                        Triple(
                                oAuth2User.attributes["email"]?.toString(),
                                oAuth2User.attributes["name"]?.toString(),
                                null
                        )
                    }
                }

        var user =
                userRepository.findByOauthProviderAndOauthId(registrationId, oauth2Id).orElse(null)

        if (user == null) {
            // Check if a user with this email hash already exists (from a different OAuth provider)
            if (email != null) {
                // Determine existing user by email hash (blind index)
                val existingUser = userRepository.findByEmailHash(BlindIndexUtil.hash(email)!!).orElse(null)
                if (existingUser != null) {
                    // Email already registered with a different OAuth provider
                    val providerName =
                            when (existingUser.oauthProvider) {
                                "naver" -> "네이버"
                                "kakao" -> "카카오"
                                "google" -> "구글"
                                "apple" -> "애플"
                                else -> existingUser.oauthProvider
                            }
                    throw OAuth2EmailAlreadyExistsException(
                            "이미 ${providerName}로 가입된 이메일입니다. ${providerName}로 로그인해주세요."
                    )
                }
            }

            // Create new user
            user =
                    User(
                            oauthProvider = registrationId,
                            oauthId = oauth2Id,
                            email = email,
                            emailHash = BlindIndexUtil.hash(email),
                            nickname = nickname,
                            gender = null,
                            birthYear = null,
                            mainLocationText = null,
                            mainLocationGeom = null,
                            profileImageUrl = profileImage,
                            username = null,
                            password = null,
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
            // Update user details if necessary (protect user-customized data)
            var changed = false
            if (!email.isNullOrBlank() && user.email != email) {
                user.email = email; user.emailHash = BlindIndexUtil.hash(email); changed = true
            }
            if (nickname != null && user.nickname == null) {
                user.nickname = nickname; changed = true
            }
            if (profileImage != null && user.profileImages.isEmpty() && user.profileImageUrl != profileImage) {
                user.profileImageUrl = profileImage
                user.profileImages.add(UserProfileImage(user = user, imageUrl = profileImage, orderIndex = 0))
                changed = true
            }
            if (changed) user = userRepository.save(user)
        }

        return UserPrincipal.create(user, oAuth2User.attributes)
    }
}
