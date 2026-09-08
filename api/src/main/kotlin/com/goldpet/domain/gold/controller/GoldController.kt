package com.goldpet.domain.gold.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.common.exception.BadRequestException
import com.goldpet.domain.common.exception.ForbiddenException
import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.gold.dto.*
import com.goldpet.domain.gold.entity.TransactionType
import com.goldpet.domain.gold.service.GoldService
import com.goldpet.domain.gold.service.IdempotencyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Gold", description = "골드/결제 API")
@RestController
@RequestMapping("/api/v1/gold")
class GoldController(
    private val goldService: GoldService,
    private val idempotencyService: IdempotencyService,
    private val systemSettingService: SystemSettingService,
) {
    companion object {
        const val PAYMENT_FLAG_KEY = "gold.payment.enabled"
        const val PAYMENT_FLAG_DEFAULT = "false"
    }

    // PG 미계약 출시 대비: 결제(충전) 마스터 토글. PhotoUrlSigner 플래그 선례.
    private fun paymentEnabled(): Boolean =
        systemSettingService.getString(PAYMENT_FLAG_KEY, PAYMENT_FLAG_DEFAULT).equals("true", ignoreCase = true)

    @Operation(summary = "내 골드 잔액")
    @GetMapping("/balance")
    fun getBalance(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<GoldBalanceResponse> {
        return ResponseEntity.ok(goldService.getBalance(principal.id))
    }

    @Operation(summary = "골드 상품 목록")
    @GetMapping("/products")
    fun getProducts(): ResponseEntity<List<GoldProductResponse>> {
        // 결제 off면 상품 비노출(DB drift 없이 결정적으로 빈 리스트)
        if (!paymentEnabled()) {
            return ResponseEntity.ok(emptyList())
        }
        return ResponseEntity.ok(goldService.getProducts())
    }

    @Operation(summary = "거래 내역")
    @GetMapping("/transactions")
    fun getTransactions(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable,
        @RequestParam(required = false) type: TransactionType?
    ): ResponseEntity<Page<TransactionResponse>> {
        return ResponseEntity.ok(goldService.getTransactions(principal.id, pageable, type))
    }

    @Operation(
        summary = "골드 충전",
        description = "IAP 후 골드 지급 처리. **Idempotency-Key 헤더 필수** — UUID 권장, 동일 key 재요청 시 저장된 응답 반환.",
    )
    @PostMapping("/charge")
    fun chargeGold(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: ChargeRequest,
    ): ResponseEntity<TransactionResponse> {
        // PG 미계약 출시: 결제 off면 충전 차단(서비스 로직 진입 전 403)
        if (!paymentEnabled()) {
            throw ForbiddenException("골드 충전 기능이 비활성화되어 있습니다.", errorCode = "PAYMENT_DISABLED")
        }
        val hash = idempotencyService.hashRequest(principal.id, "POST /api/v1/gold/charge", request)
        val response = idempotencyService.execute(
            key = idempotencyKey,
            userId = principal.id,
            requestHash = hash,
            responseType = TransactionResponse::class.java,
        ) {
            val tx = goldService.chargeGold(principal.id, request)
            tx to tx.id
        }
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "골드 사용",
        description = "**Idempotency-Key 헤더 권장** (옵션). 헤더 없으면 idempotency 보호 없음 (네트워크 retry 시 중복 차감 위험).",
    )
    @PostMapping("/spend")
    fun spendGold(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: SpendRequest,
    ): ResponseEntity<TransactionResponse> {
        // EXT-CDX-011: 클라이언트가 referenceType/referenceId 로 원장(내부 reference)을 오염시키지
        // 못하게 거부. 내부 도메인 흐름(ProfileBoost/AIProfile 등)은 서비스 계층을 직접 호출한다.
        if (request.referenceType != null || request.referenceId != null) {
            throw BadRequestException("referenceType/referenceId 는 클라이언트가 지정할 수 없습니다.")
        }
        if (idempotencyKey.isNullOrBlank()) {
            return ResponseEntity.ok(goldService.spendGold(principal.id, request))
        }
        val hash = idempotencyService.hashRequest(principal.id, "POST /api/v1/gold/spend", request)
        val response = idempotencyService.execute(
            key = idempotencyKey,
            userId = principal.id,
            requestHash = hash,
            responseType = TransactionResponse::class.java,
        ) {
            val tx = goldService.spendGold(principal.id, request)
            tx to tx.id
        }
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "골드 환불", description = "SPEND 거래에 대한 환불 처리")
    @PostMapping("/refund")
    fun refundGold(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: RefundRequest
    ): ResponseEntity<RefundResponse> {
        return ResponseEntity.ok(goldService.refundTransaction(principal.id, request))
    }
}
