package com.goldpet.domain.user.integration

import com.goldpet.IntegrationTestBase
import com.goldpet.domain.auth.dto.LoginRequest
import com.goldpet.domain.auth.dto.SignupRequest
import com.goldpet.domain.auth.exception.AccountStatusException
import com.goldpet.domain.auth.service.AuthService
import com.goldpet.domain.common.exception.ConflictException
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

/**
 * 휴면 계정 복구 및 재가입 시도 통합 테스트 — XF-07 시나리오.
 *
 * 시나리오:
 *   1. 신규 가입 (LOCAL 유저)
 *   2. 휴면 처리: UserStatus.DORMANT 설정 후 DB 반영
 *   3. 로그인 시도 → AccountStatusException(DORMANT_ACCOUNT) 발생 검증
 *   4. 휴면 활성화: UserStatus.ACTIVE 복원
 *   5. 로그인 정상 성공 (accessToken 반환 확인)
 *   6. 동일 email 로 재가입 시도 → ConflictException 발생 검증
 *      (email_hash 고유 제약 충돌 — "이미 가입된 이메일" 분기)
 */
@Tag("integration")
class DormantRecoveryReSignupIT : IntegrationTestBase() {

    @Autowired private lateinit var authService: AuthService
    @Autowired private lateinit var userRepository: UserRepository

    // PER_METHOD 라이프사이클: 반복마다 새 인스턴스 → uid 도 매번 새로 생성됨
    private val uid: String = UUID.randomUUID().toString().replace("-", "").take(10)

    @AfterEach
    fun tearDown() {
        // 가입된 유저를 username 으로 찾아 삭제 (가입 실패 시에도 안전하게 runCatching)
        runCatching {
            userRepository.findByUsername("dormant_$uid").ifPresent { userRepository.delete(it) }
        }
    }

    /**
     * 휴면 → 활성화 → 재가입 충돌 전체 흐름을 5회 반복 검증한다.
     */
    @RepeatedTest(5)
    fun `dormant-recovery full flow - login blocked while dormant, restored after activation, email reuse throws conflict`() {
        val email = "dormant_${uid}@goldpet.com"
        val username = "dormant_$uid"
        val password = "Password123!"

        val signupReq = SignupRequest(
            username = username,
            password = password,
            nickname = "DormantUser_$uid",
            name = "휴면테스트",
            birthDate = "1990-01-15",
            phoneNumber = "010-1234-5678",
            email = email,
            gender = "M",
            birthYear = 1990,
            mainLocationText = null
        )
        val loginReq = LoginRequest(username = username, password = password)

        // ── Step 1: 가입 ────────────────────────────────────────────────────────
        val signupResp = authService.signup(signupReq)
        assertThat(signupResp.accessToken).isNotBlank()
        val userId = signupResp.user.id

        // ── Step 2: 휴면 처리 ───────────────────────────────────────────────────
        val user = userRepository.findById(userId).orElseThrow()
        user.status = UserStatus.DORMANT
        userRepository.saveAndFlush(user)

        // ── Step 3: 휴면 상태 로그인 → AccountStatusException(DORMANT) ──────────
        val dormantEx = assertThrows<AccountStatusException> { authService.login(loginReq) }
        assertThat(dormantEx.userStatus).isEqualTo(UserStatus.DORMANT)
        assertThat(dormantEx.errorCode).isEqualTo("DORMANT_ACCOUNT")

        // ── Step 4: 휴면 활성화 (복구) ──────────────────────────────────────────
        user.status = UserStatus.ACTIVE
        userRepository.saveAndFlush(user)

        // ── Step 5: 활성화 후 로그인 정상 ────────────────────────────────────────
        val loginResp = authService.login(loginReq)
        assertThat(loginResp.accessToken).isNotBlank()
        assertThat(loginResp.user.id).isEqualTo(userId)

        // ── Step 6: 동일 email 재가입 → ConflictException ────────────────────────
        // username 은 달라도 email_hash 고유 제약으로 충돌 발생해야 함
        val reSignupReq = signupReq.copy(username = "dormant2_$uid", nickname = "DormantUser2_$uid")
        val conflictEx = assertThrows<ConflictException> { authService.signup(reSignupReq) }
        assertThat(conflictEx.message)
            .withFailMessage("이미 가입된 이메일 충돌 메시지가 예상과 다릅니다: ${conflictEx.message}")
            .contains("이미 가입된 이메일")
    }
}
