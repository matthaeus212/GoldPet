package com.goldpet.domain.user

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldpet.IntegrationTestBase
import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.user.dto.NotificationSettingsRequest
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * W2c 후속 FIX — "산책 리마인더"(재참여 넛지) 동의 토글의 end-to-end 영속 검증.
 *
 * 회귀 가드: 프론트가 PUT 으로 보낸 `reengagementAlert` 가 실제로
 * users.is_reengagement_alert_enabled 에 저장되고, GET 응답으로 다시 hydrate 되는지.
 * (기존엔 백엔드가 이 필드를 무시해 OFF 토글이 무음 no-op → 동의 위반이었음.)
 *
 * 실행 전: `./deploy-local/scripts/start.sh` 로 local PostGIS(5433) 기동.
 */
@Tag("integration")
class NotificationSettingsIT : IntegrationTestBase() {

    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper

    private val uid: String = UUID.randomUUID().toString().replace("-", "").take(10)
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        user = userRepository.save(
            User(
                id = 0,
                email = "ns_$uid@goldpet.com",
                emailHash = BlindIndexUtil.hash("ns_$uid@goldpet.com"),
                oauthProvider = "LOCAL", oauthId = "ns_$uid",
                username = null, password = null, nickname = "Ns_$uid",
                name = null, birthDate = null, phoneNumber = null, gender = null,
                birthYear = null, mainLocationText = null, mainLocationGeom = null,
                profileImageUrl = null,
            )
        )
    }

    @AfterEach
    fun tearDown() {
        runCatching { userRepository.deleteById(user.id) }
    }

    private fun request(reengagement: Boolean) = NotificationSettingsRequest(
        pushAlert = true,
        chatAlert = true,
        communityAlert = true,
        marketingAlert = true,
        reengagementAlert = reengagement,
    )

    private fun dbReengagementFlag(): Boolean =
        jdbc.queryForObject(
            "SELECT is_reengagement_alert_enabled FROM users WHERE id = ?",
            Boolean::class.java, user.id
        )!!

    @Test
    fun `reengagementAlert=false 가 DB에 영속되고 GET 응답으로 hydrate 된다`() {
        // 기본값 true 확인
        assertThat(userService.getNotificationSettings(user.id).reengagementAlert).isTrue()

        // OFF 토글 → 영속
        val updated = userService.updateNotificationSettings(user.id, request(reengagement = false))
        assertThat(updated.reengagementAlert).isFalse()

        // GET 재조회 hydrate
        assertThat(userService.getNotificationSettings(user.id).reengagementAlert).isFalse()
        // DB 컬럼 직접 확인 (무음 no-op 회귀 가드)
        assertThat(dbReengagementFlag()).isFalse()
    }

    @Test
    fun `reengagementAlert 토글을 다시 켜면 true 로 복귀한다`() {
        userService.updateNotificationSettings(user.id, request(reengagement = false))
        assertThat(dbReengagementFlag()).isFalse()

        userService.updateNotificationSettings(user.id, request(reengagement = true))
        assertThat(userService.getNotificationSettings(user.id).reengagementAlert).isTrue()
        assertThat(dbReengagementFlag()).isTrue()
    }

    @Test
    fun `프론트 JSON 의 reengagementAlert 필드가 요청 DTO로 역직렬화된다 (wire contract)`() {
        val json = """{"pushAlert":true,"chatAlert":true,"communityAlert":true,"marketingAlert":true,"reengagementAlert":false}"""
        val parsed = objectMapper.readValue(json, NotificationSettingsRequest::class.java)
        assertThat(parsed.reengagementAlert).isFalse()
    }
}
