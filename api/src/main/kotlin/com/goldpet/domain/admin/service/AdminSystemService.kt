package com.goldpet.domain.admin.service

import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.admin.audit.AdminAuditDetails
import com.goldpet.domain.admin.audit.AuditTarget
import com.goldpet.domain.admin.controller.AdminUserResponse
import com.goldpet.domain.admin.controller.CreateAdminRequest
import com.goldpet.domain.admin.controller.CreateAdminResponse
import com.goldpet.domain.admin.controller.ResetPasswordResponse
import com.goldpet.domain.admin.controller.SystemInfoResponse
import com.goldpet.domain.admin.controller.UpdateAdminRequest
import com.goldpet.domain.admin.entity.AdminUser
import com.goldpet.domain.admin.entity.AdminUserRole
import com.goldpet.domain.admin.repository.AdminUserRepository
import com.goldpet.domain.common.entity.AppNotice
import com.goldpet.domain.common.entity.AppNoticeType
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.exception.ConflictException
import com.goldpet.domain.common.exception.NotFoundException
import com.goldpet.domain.common.repository.AppNoticeRepository
import com.goldpet.domain.common.service.SystemSettingService
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.management.ManagementFactory
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class AdminSystemService(
    private val systemSettingService: SystemSettingService,
    private val adminUserRepository: AdminUserRepository,
    private val appNoticeRepository: AppNoticeRepository,
    private val passwordEncoder: PasswordEncoder,
    private val adminAuditService: AdminAuditService
) {
    companion object {
        private val ADMIN_PASSWORD_REGEX = AdminAuthService.ADMIN_PASSWORD_REGEX

        // 매칭 궁합 점수 설정 키 (FriendService.getCompatibleFriends 와 동일)
        private const val MATCH_ENABLED_KEY = "match.compatibility.enabled"
        private const val MATCH_W_DISTANCE_KEY = "match.score.w_distance"
        private const val MATCH_W_INTEREST_KEY = "match.score.w_interest"
        private const val MATCH_W_HOBBY_KEY = "match.score.w_hobby"
        private const val MATCH_W_TEMPERAMENT_KEY = "match.score.w_temperament"
        private const val BOOST_RANK_BONUS_KEY = "profile.boost.rank_bonus"

        // A/B 실험(궁합 매칭) 설정 키 (ExperimentService.getOrAssignCohort 와 동일, key=compatibility)
        private const val EXP_COMPAT_ENABLED_KEY = "experiment.compatibility.enabled"
        private const val EXP_COMPAT_SALT_KEY = "experiment.compatibility.salt"
        private const val EXP_COMPAT_SPLIT_KEY = "experiment.compatibility.split_pct"
        private const val EXP_COMPAT_SALT_VERSION_KEY = "experiment.compatibility.salt_version"
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    fun getSystemInfo(): SystemInfoResponse {
        val runtime = ManagementFactory.getRuntimeMXBean()
        val uptime = Duration.ofMillis(runtime.uptime)
        val uptimeStr = "${uptime.toDays()}일 ${uptime.toHoursPart()}시간 ${uptime.toMinutesPart()}분"

        val fileStore = java.nio.file.FileSystems.getDefault().fileStores.first()
        val storageTotal = fileStore.totalSpace / (1024.0 * 1024 * 1024)
        val storageUsed = (fileStore.totalSpace - fileStore.usableSpace) / (1024.0 * 1024 * 1024)

        return SystemInfoResponse(
            version = systemSettingService.getString("app.version", "1.0.0"),
            environment = System.getProperty("spring.profiles.active", "development"),
            serverTime = LocalDateTime.now().format(formatter),
            uptime = uptimeStr,
            dbStatus = "OK",
            cacheStatus = "OK",
            storageUsed = storageUsed,
            storageTotal = storageTotal
        )
    }

    fun getAdmins(): List<AdminUserResponse> {
        return adminUserRepository.findAll().map { it.toResponse() }
    }

    @Transactional
    fun createAdmin(request: CreateAdminRequest, currentAdminId: Long): CreateAdminResponse {
        val emailHash = BlindIndexUtil.hash(request.email)
            ?: throw BadRequestException("Invalid email")
        if (adminUserRepository.findByEmailHash(emailHash).isPresent) {
            throw ConflictException("이미 존재하는 이메일입니다", "ADMIN_EMAIL_DUPLICATE")
        }

        val tempPassword = request.temporaryPassword?.takeIf { it.isNotBlank() }
            ?: generateTemporaryPassword()

        if (request.temporaryPassword?.isNotBlank() == true && !ADMIN_PASSWORD_REGEX.matches(tempPassword)) {
            throw BadRequestException("비밀번호는 8자 이상이며, 영문, 숫자, 특수문자를 포함해야 합니다.")
        }

        val admin = AdminUser(
            email = request.email,
            emailHash = emailHash,
            passwordHash = passwordEncoder.encode(tempPassword),
            name = request.name,
            role = request.role,
            isActive = true,
            mustChangePassword = true
        )
        val saved = adminUserRepository.save(admin)

        adminAuditService.log(
            adminUserId = currentAdminId,
            action = "ADMIN_CREATE",
            target = AuditTarget("ADMIN_USER", saved.id),
            details = AdminAuditDetails.AdminCreate(role = saved.role, email = saved.email)
        )

        return CreateAdminResponse(
            id = saved.id,
            email = saved.email,
            name = saved.name,
            role = saved.role,
            temporaryPassword = tempPassword
        )
    }

    @Transactional
    fun updateAdmin(id: Long, request: UpdateAdminRequest, currentAdminId: Long): AdminUserResponse {
        val admin = adminUserRepository.findById(id)
            .orElseThrow { NotFoundException("관리자를 찾을 수 없습니다") }

        val isSelf = id == currentAdminId
        val newRole = request.role
        val newActive = request.isActive

        if (isSelf && newRole != null && newRole != admin.role) {
            throw BadRequestException("본인의 권한은 변경할 수 없습니다")
        }
        if (isSelf && newActive == false) {
            throw BadRequestException("본인 계정을 비활성화할 수 없습니다")
        }

        val willDemoteSuperAdmin = admin.role == AdminUserRole.SUPER_ADMIN && admin.isActive &&
            ((newRole != null && newRole != AdminUserRole.SUPER_ADMIN) || newActive == false)

        if (willDemoteSuperAdmin) {
            acquireSuperAdminLock()
            val activeSuperAdmins = countActiveSuperAdminsForUpdate()
            if (activeSuperAdmins <= 1) {
                throw ConflictException("마지막 SUPER_ADMIN 은 변경/비활성화할 수 없습니다", "LAST_SUPER_ADMIN")
            }
        }

        val before = admin.role
        request.name?.let { admin.name = it }
        newRole?.let { admin.role = it }
        newActive?.let { admin.isActive = it }
        val after = admin.role
        adminUserRepository.save(admin)

        adminAuditService.log(
            adminUserId = currentAdminId,
            action = "ADMIN_UPDATE",
            target = AuditTarget("ADMIN_USER", admin.id),
            details = AdminAuditDetails.RoleChange(before = before, after = after)
        )

        return admin.toResponse()
    }

    @Transactional
    fun deleteAdmin(id: Long, currentAdminId: Long) {
        if (id == currentAdminId) {
            throw BadRequestException("본인 계정은 삭제할 수 없습니다")
        }
        val admin = adminUserRepository.findById(id)
            .orElseThrow { NotFoundException("관리자를 찾을 수 없습니다") }

        if (admin.role == AdminUserRole.SUPER_ADMIN && admin.isActive) {
            acquireSuperAdminLock()
            if (countActiveSuperAdminsForUpdate() <= 1) {
                throw ConflictException("마지막 SUPER_ADMIN 은 삭제할 수 없습니다", "LAST_SUPER_ADMIN")
            }
        }

        admin.isActive = false
        adminUserRepository.save(admin)

        adminAuditService.log(
            adminUserId = currentAdminId,
            action = "ADMIN_DELETE",
            target = AuditTarget("ADMIN_USER", admin.id),
            details = AdminAuditDetails.AdminDelete(role = admin.role, isActive = false)
        )
    }

    @Transactional
    fun resetPassword(id: Long, currentAdminId: Long): ResetPasswordResponse {
        val admin = adminUserRepository.findById(id)
            .orElseThrow { NotFoundException("관리자를 찾을 수 없습니다") }

        val tempPassword = generateTemporaryPassword()
        admin.passwordHash = passwordEncoder.encode(tempPassword)
        admin.mustChangePassword = true
        adminUserRepository.save(admin)

        adminAuditService.log(
            adminUserId = currentAdminId,
            action = "ADMIN_PASSWORD_RESET",
            target = AuditTarget("ADMIN_USER", admin.id),
            details = AdminAuditDetails.PasswordReset(target = admin.id.toString())
        )

        return ResetPasswordResponse(temporaryPassword = tempPassword)
    }

    private fun acquireSuperAdminLock() {
        // advisory_lock_registry: 1001 = admin SUPER_ADMIN 마지막 보호 race lock
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(1001)").singleResult
    }

    private fun countActiveSuperAdminsForUpdate(): Long {
        val rows = entityManager.createNativeQuery(
            "SELECT 1 FROM admin_users WHERE role = 'SUPER_ADMIN' AND is_active = true FOR UPDATE"
        ).resultList
        return rows.size.toLong()
    }

    private fun generateTemporaryPassword(): String {
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghijkmnpqrstuvwxyz"
        val digit = "23456789"
        val special = "!@#$%^&*()-_=+"
        val all = upper + lower + digit + special
        val rng = SecureRandom()
        val chars = mutableListOf(
            upper[rng.nextInt(upper.length)],
            lower[rng.nextInt(lower.length)],
            digit[rng.nextInt(digit.length)],
            special[rng.nextInt(special.length)]
        )
        repeat(12) { chars += all[rng.nextInt(all.length)] }
        chars.shuffle(rng)
        return chars.joinToString("")
    }

    private fun AdminUser.toResponse(): AdminUserResponse = AdminUserResponse(
        id = id,
        email = email,
        name = name,
        role = role.name,
        lastLogin = lastLoginAt?.format(formatter),
        isActive = isActive,
        mustChangePassword = mustChangePassword
    )

    fun isMaintenanceMode(): Boolean {
        val now = LocalDateTime.now()
        return appNoticeRepository.findActiveByType(AppNoticeType.MAINTENANCE, now).isNotEmpty()
    }

    @Transactional
    fun setMaintenanceMode(enabled: Boolean) {
        val now = LocalDateTime.now()
        if (enabled) {
            // 이미 활성 MAINTENANCE 공지가 있으면 추가하지 않음
            val existing = appNoticeRepository.findActiveByType(AppNoticeType.MAINTENANCE, now)
            if (existing.isEmpty()) {
                appNoticeRepository.save(
                    AppNotice(
                        type = AppNoticeType.MAINTENANCE,
                        title = "서버 점검 중",
                        content = "서비스 점검 중입니다. 잠시 후 다시 시도해주세요.",
                        targetScreen = "ALL",
                        priority = 100,
                        isDismissible = false,
                        startAt = now,
                        endAt = now.plusHours(4) // 안전장치: 최대 4시간 후 자동 해제
                    )
                )
            }
        } else {
            // 활성 MAINTENANCE 공지를 모두 비활성화
            val activeNotices = appNoticeRepository.findActiveByType(AppNoticeType.MAINTENANCE, now)
            activeNotices.forEach { it.isActive = false }
            appNoticeRepository.saveAll(activeNotices)
        }
    }

    fun getAllConfigs(): List<com.goldpet.domain.admin.controller.AppConfigResponse> {
        return systemSettingService.getAllSettings()
            // A/B 무결성: 코호트 예측에 악용될 수 있는 실험 salt 류는 노출 금지(security LOW#2).
            // 이 엔드포인트엔 메서드 단위 role 게이트가 없어 VIEWER 도 읽을 수 있으므로 서버에서 제거한다.
            .filterNot { isSensitiveConfigKey(it.key) }
            .map {
                com.goldpet.domain.admin.controller.AppConfigResponse(
                    key = it.key,
                    value = it.value,
                    description = it.description
                )
            }
    }

    /** 클라이언트/하위 권한 관리자에게 평문 노출하면 안 되는 설정 키. */
    private fun isSensitiveConfigKey(key: String): Boolean =
        key.endsWith(".salt") || key.endsWith(".salt_version")

    fun updateConfig(key: String, value: String) {
        systemSettingService.setValue(key, value)
    }

    // ── 매칭 궁합 점수 설정 (W1) ─────────────────────────────────────────────
    fun getMatchingConfig(): com.goldpet.domain.admin.controller.MatchingConfigResponse {
        fun w(key: String, default: Double) =
            systemSettingService.getString(key, default.toString()).toDoubleOrNull() ?: default
        return com.goldpet.domain.admin.controller.MatchingConfigResponse(
            enabled = systemSettingService.getBoolean(MATCH_ENABLED_KEY, false),
            weightDistance = w(MATCH_W_DISTANCE_KEY, 0.40),
            weightInterest = w(MATCH_W_INTEREST_KEY, 0.25),
            weightHobby = w(MATCH_W_HOBBY_KEY, 0.20),
            weightTemperament = w(MATCH_W_TEMPERAMENT_KEY, 0.15),
            boostRankBonus = w(BOOST_RANK_BONUS_KEY, 0.15)
        )
    }

    @Transactional
    fun updateMatchingConfig(req: com.goldpet.domain.admin.controller.MatchingConfigRequest) {
        val weights = listOf(
            "거리 근접도" to req.weightDistance,
            "관심사 일치" to req.weightInterest,
            "취미 일치" to req.weightHobby,
            "기질 일치" to req.weightTemperament,
            "부스트 가산점" to req.boostRankBonus
        )
        weights.forEach { (label, v) ->
            if (v.isNaN() || v < 0.0 || v > 1.0) {
                throw BadRequestException("$label 가중치는 0~1 사이여야 합니다 (현재 $v)", "MATCH_WEIGHT_RANGE")
            }
        }
        val sum = req.weightDistance + req.weightInterest + req.weightHobby + req.weightTemperament
        if (sum < 0.99 || sum > 1.01) {
            throw BadRequestException(
                "가중치 합(거리+관심사+취미+기질)은 1.0 이어야 합니다 (현재 ${"%.2f".format(sum)})",
                "MATCH_WEIGHT_SUM"
            )
        }
        systemSettingService.setValue(MATCH_ENABLED_KEY, req.enabled.toString())
        systemSettingService.setValue(MATCH_W_DISTANCE_KEY, req.weightDistance.toString())
        systemSettingService.setValue(MATCH_W_INTEREST_KEY, req.weightInterest.toString())
        systemSettingService.setValue(MATCH_W_HOBBY_KEY, req.weightHobby.toString())
        systemSettingService.setValue(MATCH_W_TEMPERAMENT_KEY, req.weightTemperament.toString())
        systemSettingService.setValue(BOOST_RANK_BONUS_KEY, req.boostRankBonus.toString())
    }

    // ── A/B 실험 (궁합 매칭) ──────────────────────────────────────────────────
    // 궁합순 노출을 TREATMENT(궁합 스코어링)/CONTROL(거리순) 으로 가르는 write-once 코호트 실험.
    // salt 자체는 sensitive(유저가 자기 버킷 예측 못하게) — 평문 노출 없이 보유 여부/버전만 응답한다.
    fun getExperimentConfig(): com.goldpet.domain.admin.controller.ExperimentConfigResponse {
        val salt = systemSettingService.getString(EXP_COMPAT_SALT_KEY, "")
        return com.goldpet.domain.admin.controller.ExperimentConfigResponse(
            enabled = systemSettingService.getBoolean(EXP_COMPAT_ENABLED_KEY, false),
            splitPct = systemSettingService.getInt(EXP_COMPAT_SPLIT_KEY, 50),
            saltVersion = systemSettingService.getInt(EXP_COMPAT_SALT_VERSION_KEY, 1),
            hasSalt = salt.isNotBlank()
        )
    }

    @Transactional
    fun updateExperimentConfig(req: com.goldpet.domain.admin.controller.ExperimentConfigRequest) {
        if (req.splitPct < 0 || req.splitPct > 100) {
            throw BadRequestException(
                "TREATMENT 분할 비율은 0~100 사이여야 합니다 (현재 ${req.splitPct})",
                "EXP_SPLIT_RANGE"
            )
        }
        // 활성화 시 salt 가 비어 있으면 자동 생성 → 결정적 버킷팅 보장(평문 노출 안 함).
        // split/salt 변경은 ExperimentService 계약상 미할당 신규 유저에만 적용(기존 행 불변).
        if (req.enabled && systemSettingService.getString(EXP_COMPAT_SALT_KEY, "").isBlank()) {
            systemSettingService.setValue(EXP_COMPAT_SALT_KEY, java.util.UUID.randomUUID().toString())
            systemSettingService.setValue(EXP_COMPAT_SALT_VERSION_KEY, "1")
        }
        // split 을 enabled 보다 먼저 — 켜지는 순간 분할 비율이 이미 반영돼 있도록.
        systemSettingService.setValue(EXP_COMPAT_SPLIT_KEY, req.splitPct.toString())
        systemSettingService.setValue(EXP_COMPAT_ENABLED_KEY, req.enabled.toString())
    }
}
