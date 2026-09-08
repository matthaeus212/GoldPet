package com.goldpet.domain.admin.controller

import com.goldpet.domain.admin.audit.CriticalAction
import com.goldpet.domain.admin.service.AdminEconomyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin Economy Management", description = "관리자 Gold/결제 관리 API")
@RestController
@RequestMapping("/api/v1/admin/economy")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR', 'VIEWER')")
class AdminEconomyController(
    private val adminEconomyService: AdminEconomyService
) {
    @Operation(summary = "경제 통계 조회")
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<EconomyStatsResponse> {
        return ResponseEntity.ok(adminEconomyService.getStats())
    }

    @Operation(summary = "거래 내역 조회")
    @GetMapping("/transactions")
    fun getTransactions(
        @RequestParam(required = false) type: String?,
        pageable: Pageable
    ): ResponseEntity<Page<TransactionAdminResponse>> {
        return ResponseEntity.ok(adminEconomyService.getTransactions(type, pageable))
    }

    @Operation(summary = "Gold 조정")
    @PostMapping("/adjust")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @CriticalAction(action = "GOLD_ADJUST") // ≤20자 (admin_audit_logs.action VARCHAR(20))
    fun adjustGold(@RequestBody request: GoldAdjustRequest): ResponseEntity<Void> {
        adminEconomyService.adjustGold(request)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "상품 목록 조회")
    @GetMapping("/products")
    fun getProducts(): ResponseEntity<List<GoldProductAdminResponse>> {
        return ResponseEntity.ok(adminEconomyService.getProducts())
    }

    @Operation(summary = "상품 등록")
    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun createProduct(@RequestBody request: GoldProductCreateRequest): ResponseEntity<GoldProductAdminResponse> {
        return ResponseEntity.ok(adminEconomyService.createProduct(request))
    }

    @Operation(summary = "상품 수정")
    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun updateProduct(@PathVariable id: Long, @RequestBody request: GoldProductUpdateRequest): ResponseEntity<GoldProductAdminResponse> {
        return ResponseEntity.ok(adminEconomyService.updateProduct(id, request))
    }

    @Operation(summary = "상품 삭제")
    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'OPERATOR')")
    fun deleteProduct(@PathVariable id: Long): ResponseEntity<Void> {
        adminEconomyService.deleteProduct(id)
        return ResponseEntity.ok().build()
    }

    @Operation(summary = "환불 처리")
    @PostMapping("/refund")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    fun refundTransaction(@RequestBody request: AdminRefundRequest): ResponseEntity<Void> {
        adminEconomyService.refundTransaction(request)
        return ResponseEntity.ok().build()
    }
}

data class EconomyStatsResponse(
    val totalGoldCirculation: Long,
    val totalRevenue: Long,
    val todayRevenue: Long,
    val activeSubscriptions: Int,
    val pendingPayouts: Int
)

data class TransactionAdminResponse(
    val id: Long,
    val userId: Long,
    val userNickname: String,
    val type: String,
    val amount: Int,
    val description: String,
    val createdAt: String?
)

data class GoldAdjustRequest(
    val userId: Long,
    val amount: Int,
    val reason: String
)

data class GoldProductAdminResponse(
    val id: Long,
    val productCode: String,
    val name: String,
    val goldAmount: Int,
    val price: Int,
    val bonus: Int,
    val isActive: Boolean,
    val displayOrder: Int
)

data class GoldProductCreateRequest(
    val productCode: String,
    val name: String,
    val goldAmount: Int,
    val price: Int,
    val bonus: Int = 0,
    val displayOrder: Int = 0
)

data class GoldProductUpdateRequest(
    val name: String? = null,
    val goldAmount: Int? = null,
    val price: Int? = null,
    val bonus: Int? = null,
    val isActive: Boolean? = null,
    val displayOrder: Int? = null
)

data class AdminRefundRequest(
    val transactionId: Long,
    val reason: String
)
