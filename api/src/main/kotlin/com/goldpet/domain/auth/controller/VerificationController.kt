package com.goldpet.domain.auth.controller

import com.goldpet.domain.auth.dto.VerificationConfirmRequest
import com.goldpet.domain.auth.dto.VerificationRequest
import com.goldpet.domain.auth.dto.VerificationResponse
import com.goldpet.domain.auth.service.VerificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.env.Environment
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "Authentication & Verification API")
@RestController
@RequestMapping("/api/v1/auth/verification")
class VerificationController(
    private val verificationService: VerificationService,
    private val environment: Environment
) {

    @Operation(summary = "Request Verification Code", description = "인증번호를 발송합니다")
    @PostMapping("/request")
    fun requestVerificationCode(@RequestBody request: VerificationRequest): ResponseEntity<VerificationResponse> {
        val code = verificationService.sendVerificationCode(request.phoneNumber)
        // 인증코드는 local 에서만 응답에 노출(개발 편의). dev/prod 는 실제 SMS 로만 전달 —
        // dev 를 운영처럼 쓰므로 dev 노출 시 SMS 인증 우회가 가능해 local 전용으로 제한.
        val activeProfiles = environment.activeProfiles.toSet()
        val devCode = if ("local" in activeProfiles) code else null
        return ResponseEntity.ok(VerificationResponse(true, "인증번호가 발송되었습니다.", devCode))
    }

    @Operation(summary = "Confirm Verification Code", description = "Verifies the code sent to the phone number")
    @PostMapping("/confirm")
    fun confirmVerificationCode(@RequestBody request: VerificationConfirmRequest): ResponseEntity<VerificationResponse> {
        val isValid = verificationService.verifyCode(request.phoneNumber, request.code)
        
        return if (isValid) {
            ResponseEntity.ok(VerificationResponse(true, "인증되었습니다."))
        } else {
            ResponseEntity.badRequest().body(VerificationResponse(false, "인증번호가 일치하지 않거나 만료되었습니다."))
        }
    }
}
