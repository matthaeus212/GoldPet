package com.goldpet.domain.admin.service

import com.goldpet.domain.admin.controller.*
import com.goldpet.domain.common.exception.*
import com.goldpet.domain.community.repository.CommunityCommentRepository
import com.goldpet.domain.community.repository.CommunityPostRepository
import com.goldpet.domain.pet.repository.PetRepository
import com.goldpet.domain.user.entity.User
import com.goldpet.domain.user.entity.UserStatus
import com.goldpet.domain.user.repository.UserRepository
import com.goldpet.domain.user.service.UserService
import com.goldpet.domain.walk.repository.WalkRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
class AdminUserManagementService(
    private val userRepository: UserRepository,
    private val petRepository: PetRepository,
    private val postRepository: CommunityPostRepository,
    private val commentRepository: CommunityCommentRepository,
    private val walkRepository: WalkRepository,
    private val userService: UserService
) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun getUsers(pageable: Pageable, search: String?, status: String?): Page<UserListItemResponse> {
        val statusEnum = status?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UserStatus.valueOf(it) }.getOrNull() }

        // 상태 필터를 명시하지 않으면 탈퇴(WITHDRAWN) 회원은 기본 목록에서 제외한다.
        val usersPage = when {
            !search.isNullOrBlank() && statusEnum != null ->
                userRepository.findByNicknameContainingIgnoreCaseAndStatus(search, statusEnum, pageable)
            !search.isNullOrBlank() ->
                userRepository.findByNicknameContainingIgnoreCaseAndStatusNot(search, UserStatus.WITHDRAWN, pageable)
            statusEnum != null ->
                userRepository.findByStatus(statusEnum, pageable)
            else ->
                userRepository.findByStatusNot(UserStatus.WITHDRAWN, pageable)
        }

        return usersPage.map { user -> toUserListItem(user) }
    }

    fun getUserDetail(userId: Long): UserDetailResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found: $userId") }

        val pets = petRepository.findAllByOwnerId(userId)
        val postCount = postRepository.countByUserId(userId)
        val commentCount = commentRepository.countByUserId(userId)
        val walkCount = walkRepository.countByUserId(userId)
        val totalDistanceKm = walkRepository.sumDistanceByUserId(userId)

        return UserDetailResponse(
            id = user.id,
            email = safeEmail(user.email),
            nickname = user.nickname ?: "",
            intro = user.intro,
            profileImageUrl = user.profileImageUrl,
            status = user.status,
            oauthProvider = user.oauthProvider,
            goldBalance = user.goldBalance.toLong(),
            createdAt = user.createdAt.format(formatter),
            lastLoginAt = null,
            profileLockedAt = user.profileLockedAt?.format(formatter),
            pets = pets.map { pet ->
                PetSummary(
                    id = pet.id,
                    name = pet.name,
                    species = pet.species.name,
                    breed = pet.breed?.name
                )
            },
            stats = UserStats(
                totalWalks = walkCount.toInt(),
                totalDistance = totalDistanceKm,
                totalPosts = postCount.toInt(),
                totalComments = commentCount.toInt(),
                badgeCount = 0
            )
        )
    }

    @Transactional
    fun updateUserStatus(userId: Long, status: UserStatus, reason: String?) {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found: $userId") }

        user.status = status
        userRepository.save(user)
    }

    @Transactional
    fun deleteUser(userId: Long) {
        userService.adminDeleteAccount(userId, reason = "관리자에 의한 삭제")
    }

    @Transactional
    fun unlockUserProfile(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { NotFoundException("User not found: $userId") }
        user.profileLockedAt = null
        userRepository.save(user)
    }

    fun searchUsers(query: String, limit: Int): List<UserListItemResponse> {
        return userRepository.findByNicknameContainingIgnoreCase(query, Pageable.ofSize(limit))
            .content
            .map { toUserListItem(it) }
    }

    private fun toUserListItem(user: User): UserListItemResponse {
        val petCount = petRepository.countByOwnerId(user.id)
        val postCount = postRepository.countByUserId(user.id)
        val walkCount = walkRepository.countByUserId(user.id)

        return UserListItemResponse(
            id = user.id,
            email = safeEmail(user.email),
            nickname = user.nickname ?: "",
            profileImageUrl = user.profileImageUrl,
            status = user.status,
            petCount = petCount.toInt(),
            walkCount = walkCount.toInt(),
            postCount = postCount.toInt(),
            createdAt = user.createdAt.format(formatter),
            lastLoginAt = null
        )
    }

    // Guard: legitimate emails always contain '@'. Ciphertext leakage (e.g. from prior rotation
    // corruption) would not — suppress it instead of exposing garbage in the admin UI.
    private fun safeEmail(email: String?): String? {
        if (email.isNullOrBlank()) return null
        return if (email.contains('@')) email else null
    }
}
