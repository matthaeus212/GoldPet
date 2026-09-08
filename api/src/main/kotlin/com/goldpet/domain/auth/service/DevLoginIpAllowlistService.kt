// dev-login 허용 IP 목록 관리 (잠김 방지 가드 포함)
package com.goldpet.domain.auth.service

import com.goldpet.domain.auth.entity.DevLoginIpAllow
import com.goldpet.domain.auth.repository.DevLoginIpAllowRepository
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.exception.ConflictException
import com.goldpet.domain.common.exception.NotFoundException
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * dev-login 허용 IP 의 CRUD.
 *
 * ## 잠김(lockout) 방지가 이 서비스의 핵심 책임이다
 * 허용목록을 잘못 고치면 **오너 본인이 dev-login 에서 잠긴다.** 그리고 잠긴 사람은 당황해서
 * `0.0.0.0/0` 같은 걸 넣는다 — 그 순간 통제는 장식이 된다. 그래서 실수를 미리 막는다:
 *
 *  - **자기잠김 가드**: 변경 후에도 요청자의 현재 IP 가 최소 1개 활성 엔트리에 매칭돼야 한다.
 *    아니면 409. 정말 의도한 것이면 `confirmSelfLockout=true` 로 명시해야 한다.
 *  - **광역 CIDR 거부**: IPv4 `/24`, IPv6 `/48` 보다 넓은 prefix 는 거부한다.
 *  - **마지막 활성 엔트리 삭제 금지**: 0개가 되면 dev-login 이 전면 차단된다(정책이 fail-closed).
 *    끄고 싶으면 킬스위치(`devlogin.enabled=false`)를 쓰라 — 그게 명시적이다.
 */
@Service
class DevLoginIpAllowlistService(
    private val repository: DevLoginIpAllowRepository
) {
    @Transactional(readOnly = true)
    fun findAll(): List<DevLoginIpAllow> = repository.findAll().sortedBy { it.id }

    /** 정책이 쓰는 현재 유효 패턴. 캐시하지 않는다 — 어드민 변경이 즉시 반영돼야 한다. */
    @Transactional(readOnly = true)
    fun activePatterns(): List<String> =
        repository.findActive(LocalDateTime.now()).map { it.ipPattern }

    @Transactional
    fun create(
        ipPattern: String,
        label: String,
        expiresAt: LocalDateTime?,
        adminId: Long
    ): DevLoginIpAllow {
        val pattern = normalize(ipPattern)
        validatePattern(pattern)
        if (repository.findByIpPattern(pattern).isPresent) {
            throw ConflictException("이미 등록된 IP 입니다: $pattern")
        }
        require(label.isNotBlank()) { "라벨은 필수입니다" }

        return repository.save(
            DevLoginIpAllow(
                ipPattern = pattern,
                label = label.trim(),
                enabled = true,
                expiresAt = expiresAt,
                createdBy = adminId
            )
        )
        // 추가는 잠김을 만들 수 없으므로 자기잠김 가드가 필요 없다.
    }

    @Transactional
    fun update(
        id: Long,
        label: String?,
        enabled: Boolean?,
        expiresAt: LocalDateTime?,
        clearExpiry: Boolean,
        requesterIp: String?,
        confirmSelfLockout: Boolean
    ): DevLoginIpAllow {
        val entry = repository.findById(id).orElseThrow { NotFoundException("허용 IP 를 찾을 수 없습니다: $id") }

        label?.let {
            require(it.isNotBlank()) { "라벨은 비울 수 없습니다" }
            entry.label = it.trim()
        }
        enabled?.let { entry.enabled = it }
        if (clearExpiry) entry.expiresAt = null else expiresAt?.let { entry.expiresAt = it }
        entry.updatedAt = LocalDateTime.now()

        repository.save(entry)
        guardAgainstLockout(requesterIp, confirmSelfLockout)
        return entry
    }

    @Transactional
    fun delete(id: Long, requesterIp: String?, confirmSelfLockout: Boolean) {
        val entry = repository.findById(id).orElseThrow { NotFoundException("허용 IP 를 찾을 수 없습니다: $id") }

        val activeCount = repository.findActive(LocalDateTime.now()).size
        if (entry.isActive() && activeCount <= 1) {
            throw ConflictException(
                "마지막 활성 IP 는 삭제할 수 없습니다. dev-login 을 끄려면 킬스위치(devlogin.enabled=false)를 쓰세요."
            )
        }

        repository.delete(entry)
        repository.flush()
        guardAgainstLockout(requesterIp, confirmSelfLockout)
    }

    /**
     * 변경 후에도 요청자가 여전히 통과하는지 확인한다. 아니면 트랜잭션을 되돌린다(예외 → 롤백).
     *
     * requesterIp 가 null 이면(= X-Real-IP 판정 불가) 판단할 수 없으므로 통과시킨다 —
     * 어차피 그 상태로는 dev-login 자체를 못 쓴다(정책이 fail-closed).
     */
    private fun guardAgainstLockout(requesterIp: String?, confirmSelfLockout: Boolean) {
        if (confirmSelfLockout || requesterIp == null) return

        val stillAllowed = repository.findActive(LocalDateTime.now()).any { entry ->
            try {
                IpAddressMatcher(entry.ipPattern).matches(requesterIp)
            } catch (_: IllegalArgumentException) {
                false
            }
        }
        if (!stillAllowed) {
            throw ConflictException(
                "이 변경을 적용하면 현재 IP($requesterIp)에서 dev-login 에 접근할 수 없게 됩니다. " +
                    "의도한 것이라면 confirmSelfLockout=true 로 다시 요청하세요."
            )
        }
    }

    private fun normalize(raw: String): String = raw.trim()

    /**
     * 패턴 검증.
     *
     * 광역 CIDR 을 막는 이유: 잠긴 사람이 급한 마음에 `0.0.0.0/0` 을 넣으면 통제가 사라진다.
     * 그런 실수는 되돌리기 어렵고(아무도 다시 안 본다) 조용하다.
     */
    private fun validatePattern(pattern: String) {
        if (pattern.isBlank()) throw BadRequestException("IP 를 입력하세요")

        // 형식 검증은 실제 매칭기로 한다(IPv4/IPv6/CIDR 전부 지원).
        try {
            IpAddressMatcher(pattern)
        } catch (e: IllegalArgumentException) {
            throw BadRequestException("올바른 IP 또는 CIDR 이 아닙니다: $pattern")
        }

        val slash = pattern.indexOf('/')
        if (slash < 0) return   // 단일 IP = /32 또는 /128

        val prefix = pattern.substring(slash + 1).toIntOrNull()
            ?: throw BadRequestException("올바른 CIDR 이 아닙니다: $pattern")
        val isIpv6 = pattern.substring(0, slash).contains(':')

        val minPrefix = if (isIpv6) MIN_PREFIX_V6 else MIN_PREFIX_V4
        if (prefix < minPrefix) {
            throw BadRequestException(
                "너무 넓은 대역입니다($pattern). " +
                    "IPv4 는 /$MIN_PREFIX_V4, IPv6 는 /$MIN_PREFIX_V6 보다 좁아야 합니다."
            )
        }
    }

    companion object {
        const val MIN_PREFIX_V4 = 24
        const val MIN_PREFIX_V6 = 48
    }
}
