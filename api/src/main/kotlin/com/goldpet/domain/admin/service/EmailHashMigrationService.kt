// 이메일 블라인드 인덱스(emailHash) 재계산·중복 병합 마이그레이션 (관리자 1회성 배치)
package com.goldpet.domain.admin.service

import com.goldpet.config.crypto.BlindIndexUtil
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ARCH-005: 이 마이그레이션 로직은 UserMigrationController 안에 통째로 들어 있었고,
 * 컨트롤러가 UserRepository 를 직접 주입받아 전체 사용자 스캔·저장까지 수행했다.
 * HTTP 계층은 호출과 응답 매핑만 하도록 배치 로직을 서비스로 옮긴다.
 */
@Service
class EmailHashMigrationService(
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class RehashResult(
        val totalUsers: Int,
        val fixed: Int,
        val skipped: Int,
        val duplicates: Int
    )

    data class MigrateResult(
        val totalUsersScanned: Int,
        val updatedCount: Int,
        val duplicateGroupsFound: Int,
        val mergedUsersCount: Int
    )

    /**
     * 모든 사용자의 emailHash 를 복호화된 email 로부터 재계산한다.
     * 과거 BlindIndexConverter 의 이중 해싱 손상을 복구한다.
     */
    @Transactional
    fun rehashAllEmailHashes(): RehashResult {
        val users = userRepository.findAll()
        var fixedCount = 0
        var skippedCount = 0
        var duplicateCount = 0
        val usedHashes = mutableSetOf<String>()

        // First pass: collect currently correct hashes to detect collisions
        for (user in users) {
            val email = user.email
            if (email == null || user.status == UserStatus.WITHDRAWN) continue
            val correctHash = BlindIndexUtil.hash(email) ?: continue
            if (user.emailHash == correctHash) {
                usedHashes.add(correctHash)
            }
        }

        // Second pass: fix corrupted hashes, handle duplicates
        for (user in users) {
            val email = user.email
            if (email == null || user.status == UserStatus.WITHDRAWN) {
                skippedCount++
                continue
            }
            val correctHash = BlindIndexUtil.hash(email) ?: continue
            if (user.emailHash == correctHash) {
                usedHashes.add(correctHash)
                skippedCount++
                continue
            }
            if (usedHashes.contains(correctHash)) {
                // Another user already has this hash - suffix with user id
                val dedupHash = "${correctHash}-dup-${user.id}"
                log.warn("Duplicate email detected for user ${user.id} (${user.nickname}). Using dedup hash.")
                user.emailHash = dedupHash
                userRepository.save(user)
                duplicateCount++
            } else {
                log.info("Fixing emailHash for user ${user.id} (${user.nickname})")
                user.emailHash = correctHash
                usedHashes.add(correctHash)
                userRepository.save(user)
                fixedCount++
            }
        }

        return RehashResult(
            totalUsers = users.size,
            fixed = fixedCount,
            skipped = skippedCount,
            duplicates = duplicateCount
        )
    }

    /**
     * emailHash 가 비어 있는 사용자에게 해시를 채우고, 같은 이메일이 여러 계정에 걸쳐 있으면
     * 가장 최근 계정만 남기고 나머지는 WITHDRAWN 으로 병합 처리한다.
     */
    @Transactional
    fun migrateEmailHash(): MigrateResult {
        val users = userRepository.findAll()
        val emailMap = mutableMapOf<String, MutableList<User>>()
        var updatedCount = 0
        var duplicateCount = 0
        var mergedCount = 0

        // 1. emailHash 가 아직 없는 사용자만 평문 이메일로 그룹핑 (엔티티 컨버터가 복호화해 준다)
        for (user in users) {
            val email = user.email ?: continue
            if (user.emailHash == null) {
                emailMap.computeIfAbsent(email) { mutableListOf() }.add(user)
            }
        }

        // 2. 그룹 처리
        for ((email, userList) in emailMap) {
            if (userList.size > 1) {
                duplicateCount++

                // 최신 계정을 남긴다 (createdAt 내림차순)
                userList.sortByDescending { it.createdAt }
                val mainUser = userList[0]
                log.info("Duplicate found for email: $email. Keeping User ID: ${mainUser.id}")

                mainUser.emailHash = BlindIndexUtil.hash(email)
                userRepository.save(mainUser)
                updatedCount++

                for (i in 1 until userList.size) {
                    val duplicateUser = userList[i]
                    log.info(
                        "Merging/Deactivating duplicate User ID: {}, Provider: {}",
                        duplicateUser.id, duplicateUser.oauthProvider
                    )
                    // 데이터 병합(골드 합산·펫 이관 등)은 하지 않고 로그인만 차단한다.
                    duplicateUser.status = UserStatus.WITHDRAWN
                    duplicateUser.nickname = "${duplicateUser.nickname}_merged_${mainUser.id}"
                    duplicateUser.email = null
                    duplicateUser.emailHash = "MERGED_${duplicateUser.id}"   // 해시 충돌 회피
                    duplicateUser.oauthId = "MERGED_${duplicateUser.oauthId}" // oauth 조회 회피
                    userRepository.save(duplicateUser)
                    mergedCount++
                }
            } else {
                val user = userList[0]
                user.emailHash = BlindIndexUtil.hash(email)
                userRepository.save(user)
                updatedCount++
            }
        }

        return MigrateResult(
            totalUsersScanned = users.size,
            updatedCount = updatedCount,
            duplicateGroupsFound = duplicateCount,
            mergedUsersCount = mergedCount
        )
    }
}
