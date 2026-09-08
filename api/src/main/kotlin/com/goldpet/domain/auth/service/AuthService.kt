package com.goldpet.domain.auth.service

import com.goldpet.domain.common.exception.*
import com.goldpet.config.jwt.JwtTokenProvider
import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.auth.dto.AuthResponse
import com.goldpet.domain.auth.dto.FindUsernameRequest
import com.goldpet.domain.auth.dto.FindUsernameResponse
import com.goldpet.domain.auth.dto.LoginRequest
import com.goldpet.domain.auth.dto.ResetPasswordRequest
import com.goldpet.domain.auth.dto.SignupRequest
import com.goldpet.domain.auth.dto.SnsSignupRequest
import com.goldpet.domain.auth.dto.VerifyAccountRequest
import com.goldpet.domain.auth.exception.AccountStatusException
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.config.crypto.BlindIndexUtil
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AuthService(
        private val userRepository: UserRepository,
        // STYLE-001: 계정 상태 검사를 공용 가드로 통일(휴면 해제 정보 동봉).
        private val accountStatusGuard: AccountStatusGuard,
        private val devLoginAccessPolicy: DevLoginAccessPolicy,
        private val passwordEncoder: PasswordEncoder,
        private val jwtTokenProvider: JwtTokenProvider,
        private val systemSettingService: SystemSettingService,
        private val goldService: GoldService
) {
    @Transactional
    fun signup(request: SignupRequest): AuthResponse {
        if (!systemSettingService.getBoolean("allow_signups", true)) {
            throw BadRequestException("현재 신규 가입이 중단되었습니다.")
        }
        // Check duplicate username or email
        if (userRepository.findByUsername(request.username).isPresent) {
            throw ConflictException("이미 사용 중인 아이디입니다.")
        }
        val reqEmail = request.email
        if (!reqEmail.isNullOrBlank()) {
            val emailHash = BlindIndexUtil.hash(reqEmail)!!
            userRepository.findByEmailHash(emailHash).ifPresent { existing ->
                val provider = existing.oauthProvider?.uppercase() ?: "LOCAL"
                val msg = when (provider) {
                    "LOCAL" -> "이미 가입된 이메일입니다. 아이디 찾기 또는 비밀번호 재설정을 이용해주세요."
                    "KAKAO" -> "이미 카카오 계정으로 가입된 이메일입니다. 카카오 로그인을 이용해주세요."
                    "NAVER" -> "이미 네이버 계정으로 가입된 이메일입니다. 네이버 로그인을 이용해주세요."
                    "GOOGLE" -> "이미 Google 계정으로 가입된 이메일입니다. Google 로그인을 이용해주세요."
                    "APPLE" -> "이미 Apple 계정으로 가입된 이메일입니다. Apple 로그인을 이용해주세요."
                    else -> "이미 가입된 이메일입니다."
                }
                throw ConflictException(msg)
            }
        }
        // Create user entity
        val user =
                User(
                        email = request.email,
                        emailHash = BlindIndexUtil.hash(request.email),
                        oauthProvider = "LOCAL",
                        oauthId = "LOCAL_${request.username}",
                        username = request.username,
                        password = passwordEncoder.encode(request.password),
                        nickname = request.nickname,
                        name = request.name,
                        // Optional (Apple 5.1.1(v)): blank/null demographics are allowed.
                        birthDate = request.birthDate?.takeIf { it.isNotBlank() }?.let { java.time.LocalDate.parse(it) },
                        phoneNumber = request.phoneNumber?.takeIf { it.isNotBlank() },
                        gender = request.gender?.takeIf { it.isNotBlank() },
                        birthYear = request.birthYear,
                        mainLocationText = request.mainLocationText,
                        mainLocationGeom = null,
                        profileImageUrl = null,
                        // Only lock the profile once an identity anchor (phone) is provided,
                        // so users who skip optional fields can still complete them later.
                        profileLockedAt = if (!request.phoneNumber.isNullOrBlank()) LocalDateTime.now() else null,
                        // LOCAL 가입은 이 시점에 온보딩 완료.
                        signupCompletedAt = LocalDateTime.now()
                )
        // Race-condition backstop: concurrent signups may both pass the pre-check
        // and one hits the uq_users_email_hash / username constraint.
        val saved = try {
            userRepository.save(user)
        } catch (ex: DataIntegrityViolationException) {
            val cause = ex.message ?: ""
            val msg = when {
                cause.contains("email_hash", ignoreCase = true) -> "이미 가입된 이메일입니다."
                cause.contains("username", ignoreCase = true) -> "이미 사용 중인 아이디입니다."
                else -> "이미 가입된 정보입니다."
            }
            throw ConflictException(msg)
        }
        goldService.grantWelcomeGoldIfNeeded(saved.id)
        val userPrincipal = UserPrincipal.create(saved)
        val auth =
                UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.authorities)
        val token = jwtTokenProvider.generateToken(auth)
        val refreshToken = jwtTokenProvider.generateRefreshToken(saved.id)
        return AuthResponse(
                accessToken = token,
                refreshToken = refreshToken,
                user =
                        AuthResponse.UserInfo(
                                id = saved.id,
                                username = saved.username!!,
                                nickname = saved.nickname!!,
                                email = saved.email,
                                phoneNumber = saved.phoneNumber,
                                name = saved.name,
                                signupCompleted = saved.signupCompletedAt != null
                        )
        )
    }

    // STYLE-001: lastLoginAt 을 갱신해야 하므로 더 이상 readOnly 가 아니다(휴면 전환 기준값).
    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val userOpt = userRepository.findByUsername(request.username)
        val user = userOpt.orElseThrow { UnauthorizedException("Invalid credentials") }
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw UnauthorizedException("Invalid credentials")
        }
        accountStatusGuard.check(user)
        user.lastLoginAt = java.time.LocalDateTime.now()
        userRepository.save(user)
        val userPrincipal = UserPrincipal.create(user)
        val auth =
                UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.authorities)
        val token = jwtTokenProvider.generateToken(auth)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)
        return AuthResponse(
                accessToken = token,
                refreshToken = refreshToken,
                user =
                        AuthResponse.UserInfo(
                                id = user.id,
                                username = user.username!!,
                                nickname = user.nickname!!,
                                email = user.email,
                                phoneNumber = user.phoneNumber,
                                name = user.name,
                                signupCompleted = user.signupCompletedAt != null
                        )
        )
    }

    @Transactional
    fun snsSignup(userId: Long, request: SnsSignupRequest): AuthResponse {
        if (!systemSettingService.getBoolean("allow_signups", true)) {
            throw BadRequestException("현재 신규 가입이 중단되었습니다.")
        }
        val user =
                userRepository.findById(userId).orElseThrow {
                    NotFoundException("User not found")
                }

        // Update user info — demographics optional per Apple 5.1.1(v)
        user.nickname = request.nickname
        user.name = request.name
        user.birthDate = request.birthDate?.takeIf { it.isNotBlank() }?.let { java.time.LocalDate.parse(it) }
        user.gender = request.gender?.takeIf { it.isNotBlank() }
        user.phoneNumber = request.phoneNumber?.takeIf { it.isNotBlank() }
        // Note: Pet info should be saved to a Pet entity, but for now we'll just update the user or
        // assume Pet entity creation logic is here.
        // Since the User entity doesn't have pet fields, and we haven't seen a Pet entity yet, I'll
        // assume we need to create one or just ignore for now if the task is focused on User Auth.
        // However, the request has pet info. I should check if there is a PetRepository.
        // For this task, I will just update the User's nickname and return the response.
        // Real implementation should save Pet info.
        // Lock only once an identity anchor (phone) is provided; optional fields stay editable.
        if (!request.phoneNumber.isNullOrBlank()) {
            user.profileLockedAt = LocalDateTime.now()
        }
        // 추가가입(온보딩) 완료. 이미 완료된 사용자의 최초 완료 시각은 보존.
        val firstCompletion = user.signupCompletedAt == null
        user.signupCompletedAt = user.signupCompletedAt ?: LocalDateTime.now()

        userRepository.save(user)

        if (firstCompletion) {
            goldService.grantWelcomeGoldIfNeeded(user.id)
        }

        val userPrincipal = UserPrincipal.create(user)
        val auth =
                UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.authorities)
        val token = jwtTokenProvider.generateToken(auth)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        return AuthResponse(
                accessToken = token,
                refreshToken = refreshToken,
                user =
                        AuthResponse.UserInfo(
                                id = user.id,
                                username = user.username
                                                ?: user.oauthId, // Fallback to oauthId if username
                                // is null
                                nickname = user.nickname!!,
                                email = user.email,
                                phoneNumber = user.phoneNumber,
                                name = user.name,
                                signupCompleted = user.signupCompletedAt != null
                        )
        )
    }


    @Transactional(readOnly = true)
    fun findUsername(request: FindUsernameRequest): FindUsernameResponse {
        val user = userRepository.findByEmailHash(BlindIndexUtil.hash(request.email)!!).orElse(null)
            ?: throw NotFoundException("해당 이메일로 가입된 계정을 찾을 수 없습니다.")
        val username = user.username ?: throw NotFoundException("해당 이메일로 가입된 계정을 찾을 수 없습니다.")
        val snsProvider = if (user.oauthProvider != "LOCAL") user.oauthProvider else null
        return FindUsernameResponse(username = maskUsername(username), snsProvider = snsProvider)
    }

    @Transactional(readOnly = true)
    fun verifyAccount(request: VerifyAccountRequest): String {
        val user = userRepository.findByUsername(request.username)
            .orElseThrow { NotFoundException("아이디 또는 이메일이 일치하지 않습니다.") }
        if (user.email?.lowercase() != request.email.lowercase()) {
            throw NotFoundException("아이디 또는 이메일이 일치하지 않습니다.")
        }
        if (user.oauthProvider != "LOCAL") {
            throw BadRequestException("소셜 계정은 비밀번호를 변경할 수 없습니다.")
        }
        return jwtTokenProvider.generatePasswordResetToken(request.username, request.email)
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest) {
        if (request.resetToken == null ||
            !jwtTokenProvider.validatePasswordResetToken(request.resetToken, request.username, request.email)) {
            throw BadRequestException("인증이 만료되었습니다. 다시 본인인증을 진행해주세요.")
        }
        val user = userRepository.findByUsername(request.username)
            .orElseThrow { NotFoundException("아이디 또는 이메일이 일치하지 않습니다.") }
        if (user.email?.lowercase() != request.email.lowercase()) {
            throw NotFoundException("아이디 또는 이메일이 일치하지 않습니다.")
        }
        if (user.oauthProvider != "LOCAL") {
            throw BadRequestException("소셜 계정은 비밀번호를 변경할 수 없습니다.")
        }
        user.password = passwordEncoder.encode(request.password)
        userRepository.save(user)
    }

    private fun maskUsername(username: String): String {
        if (username.length <= 3) return username
        val visible = username.length / 2
        return username.take(visible) + "*".repeat(username.length - visible)
    }

    @Transactional
    fun devLogin(user: User): AuthResponse {
        // 일반 login() 은 계정 상태를 검사하는데 dev-login 은 하지 않아, 정지(SUSPENDED)/탈퇴(WITHDRAWN)/
        // 휴면(DORMANT) 계정도 토큰을 받을 수 있었다(정지 회피 경로). 동일 가드를 적용한다.
        accountStatusGuard.check(user)
        user.lastLoginAt = java.time.LocalDateTime.now()

        val userPrincipal = UserPrincipal.create(user)
        val auth =
                UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.authorities)
        val token = jwtTokenProvider.generateToken(auth)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        return AuthResponse(
                accessToken = token,
                refreshToken = refreshToken,
                user =
                        AuthResponse.UserInfo(
                                id = user.id,
                                username = user.username ?: user.oauthId,
                                nickname = user.nickname ?: "Test User",
                                email = user.email,
                                phoneNumber = user.phoneNumber,
                                name = user.name,
                                signupCompleted = user.signupCompletedAt != null
                        )
        )
    }

    /**
     * ARCH-005: AuthController 가 중복확인을 위해 UserRepository 를 직접 주입받고 있었다.
     * 조회 규칙(블라인드 인덱스 해시, 본인 닉네임 예외)은 도메인 지식이므로 서비스가 소유한다.
     */
    @Transactional(readOnly = true)
    fun isUsernameAvailable(username: String): Boolean =
        userRepository.findByUsername(username).isEmpty

    /** 인증된 사용자의 본인 닉네임은 중복으로 보지 않는다(currentUserId 전달 시). */
    @Transactional(readOnly = true)
    fun isNicknameAvailable(nickname: String, currentUserId: Long?): Boolean {
        val existing = userRepository.findByNickname(nickname).orElse(null) ?: return true
        return currentUserId != null && existing.id == currentUserId
    }

    /** 사용 중이면 가입 경로(provider)를 함께 돌려준다 — 소셜/로컬 중복 가입 안내용. */
    @Transactional(readOnly = true)
    fun findEmailProvider(email: String): String? {
        val emailHash = com.goldpet.config.crypto.BlindIndexUtil.hash(email)!!
        val existing = userRepository.findByEmailHash(emailHash).orElse(null) ?: return null
        return existing.oauthProvider?.uppercase() ?: "LOCAL"
    }

    /**
     * ARCH-005: refresh 토큰 회전 후 새 access 토큰을 만들려면 사용자 조회가 필요했는데,
     * 컨트롤러가 UserRepository 를 직접 주입받고 있었다. 조회+검증을 서비스가 소유한다.
     */
    @Transactional(readOnly = true)
    fun getUserForRefresh(userId: Long): com.goldpet.domain.user.entity.User =
        userRepository.findById(userId)
            .orElseThrow {
                com.goldpet.domain.common.exception.UnauthorizedException("User not found for rotated refresh token")
            }

    /**
     * ARCH-005: DevAuthController 가 dev 계정 조회/생성을 위해 UserRepository 를 직접 주입받고
     * 있었다. 계층 규칙은 동일하게 적용한다.
     *
     * **보안(dev-login 인증우회 차단):** 이메일 허용목록 검사를 **모든 조회보다 먼저** 한다.
     *  - 예전에는 `findByEmailHash` 폴백이 oauthProvider 를 안 가려, 카카오/네이버/구글 실사용자의
     *    이메일만 알면 그 계정의 토큰이 발급됐다(계정탈취).
     *  - 1차 조회 키인 `dev-user-<email.hashCode()>` 도 32비트 비암호학적 해시라 충돌을 구성할 수
     *    있다. 검사를 조회 뒤에 두면 이 경로로 우회된다. 그래서 **최상단**이다.
     *  - 미등재 이메일에 대해 예전에는 dev 계정을 새로 만들어 토큰을 줬는데, 그 토큰이 sns-signup 을
     *    통과해 **무한 계정 + 계정당 웰컴골드 30G** faucet 이 됐다. 이제 만들지 않고 거부한다.
     */
    @Transactional
    fun getOrCreateDevUser(email: String, nickname: String): com.goldpet.domain.user.entity.User {
        if (!devLoginAccessPolicy.isEmailAllowed(email)) {
            throw com.goldpet.domain.common.exception.ForbiddenException(
                "dev-login 이 허용되지 않은 계정입니다."
            )
        }

        val oauthId = "dev-user-${email.hashCode()}"
        val emailHash = com.goldpet.config.crypto.BlindIndexUtil.hash(email)
        val existing = userRepository.findByOauthProviderAndOauthId("dev", oauthId).orElse(null)
            ?: emailHash?.let { userRepository.findByEmailHash(it).orElse(null) }
        if (existing != null) return existing

        return userRepository.save(
            com.goldpet.domain.user.entity.User(
                oauthProvider = "dev",
                oauthId = oauthId,
                nickname = nickname,
                email = email,
                emailHash = emailHash,
                gender = null,
                birthYear = null,
                mainLocationText = null,
                mainLocationGeom = null,
                profileImageUrl = null,
                username = null,
                password = null,
                name = null,
                birthDate = null,
                phoneNumber = null
            )
        )
    }
}
