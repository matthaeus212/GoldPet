package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.service.AdminEconomyService
import com.goldpet.domain.admin.service.AdminUserManagementService
import com.goldpet.domain.user.entity.UserStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

data class UserListItemResponse(
    val id: Long,
    val email: String?,
    val nickname: String,
    val profileImageUrl: String?,
    val status: UserStatus,
    val petCount: Int,
    val walkCount: Int,
    val postCount: Int,
    val createdAt: String?,
    val lastLoginAt: String?
)

data class UserDetailResponse(
    val id: Long,
    val email: String?,
    val nickname: String,
    val intro: String?,
    val profileImageUrl: String?,
    val status: UserStatus,
    val oauthProvider: String?,
    val goldBalance: Long,
    val createdAt: String?,
    val lastLoginAt: String?,
    val profileLockedAt: String?,
    val pets: List<PetSummary>,
    val stats: UserStats
)

data class PetSummary(
    val id: Long,
    val name: String,
    val species: String,
    val breed: String?
)

data class UserStats(
    val totalWalks: Int,
    val totalDistance: Double,
    val totalPosts: Int,
    val totalComments: Int,
    val badgeCount: Int
)

data class UpdateUserStatusRequest(
    val status: UserStatus,
    val reason: String?
)

@Tag(name = "Admin User Management", description = "관리자 회원 관리 API")
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminUserManagementController(
    private val userManagementService: AdminUserManagementService,
    private val adminEconomyService: AdminEconomyService
) {

    @Operation(summary = "회원 목록 조회")
    @GetMapping
    fun getUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "createdAt") sortBy: String,
        @RequestParam(defaultValue = "desc") sortDir: String
    ): ResponseEntity<Page<UserListItemResponse>> {
        val sort = if (sortDir == "asc") Sort.by(sortBy).ascending() else Sort.by(sortBy).descending()
        val pageable = PageRequest.of(page, size, sort)
        return ResponseEntity.ok(userManagementService.getUsers(pageable, search, status))
    }

    @Operation(summary = "회원 상세 조회")
    @GetMapping("/{userId}")
    fun getUserDetail(@PathVariable userId: Long): ResponseEntity<UserDetailResponse> {
        return ResponseEntity.ok(userManagementService.getUserDetail(userId))
    }

    @Operation(summary = "회원별 골드 거래 내역 조회")
    @GetMapping("/{userId}/gold-transactions")
    fun getUserGoldTransactions(
        @PathVariable userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Page<TransactionAdminResponse>> {
        val pageable: Pageable = PageRequest.of(page, size)
        return ResponseEntity.ok(adminEconomyService.getUserTransactions(userId, pageable))
    }

    @Operation(summary = "회원 상태 변경 (정지/해제)")
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun updateUserStatus(
        @PathVariable userId: Long,
        @RequestBody request: UpdateUserStatusRequest
    ): ResponseEntity<Void> {
        userManagementService.updateUserStatus(userId, request.status, request.reason)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "회원 삭제 (탈퇴 처리)")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun deleteUser(@PathVariable userId: Long): ResponseEntity<Void> {
        userManagementService.deleteUser(userId)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "회원 검색")
    @GetMapping("/search")
    fun searchUsers(
        @RequestParam query: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<UserListItemResponse>> {
        return ResponseEntity.ok(userManagementService.searchUsers(query, limit))
    }

    @Operation(summary = "프로필 잠금 해제 (인증 필드 재변경 허용)")
    @PutMapping("/{userId}/unlock-profile")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun unlockUserProfile(@PathVariable userId: Long): ResponseEntity<Map<String, String>> {
        userManagementService.unlockUserProfile(userId)
        return ResponseEntity.ok(mapOf("message" to "Profile unlocked for user $userId"))
    }
}
