package com.goldpet.domain.gold.controller

import com.goldpet.config.security.UserPrincipal
import com.goldpet.domain.gold.dto.IapVerifyReceiptRequest
import com.goldpet.domain.gold.dto.IapVerifyReceiptResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Gold", description = "골드/결제 API")
@RestController
@RequestMapping("/api/v1/gold/iap")
class IapController {

    @Operation(
        summary = "IAP 영수증 검증",
        description = """
            인앱결제(IAP) 영수증을 서버에서 검증하고 골드를 지급합니다.

            **현재 상태: NOT_IMPLEMENTED** — 법인 개발자 계정 전환 후 활성화됩니다.
            골드는 지급되지 않으며 verified=false를 반환합니다.
        """,
    )
    @PostMapping("/verify-receipt")
    fun verifyReceipt(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: IapVerifyReceiptRequest,
    ): ResponseEntity<IapVerifyReceiptResponse> {
        // TODO: implement once corp Apple/Google developer accounts are provisioned.
        // See .omc/plans/iap-integration-design.md for full architecture.
        return ResponseEntity.ok(
            IapVerifyReceiptResponse(
                verified = false,
                status = "NOT_IMPLEMENTED",
                goldGranted = 0,
                message = "IAP 영수증 검증은 법인 개발자 계정 전환 후 활성화됩니다.",
            )
        )
    }
}
