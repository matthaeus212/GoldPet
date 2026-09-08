package com.goldpet.domain.notification.service

/**
 * V69 — Sprint 4 BLOCKER #8. FCM 송신 결과 — invalid token 자동 cleanup 신호.
 *
 * 기존 [FcmPushSender.send] 의 Boolean 반환은 invalid token 과 transient failure 를 구분 못함.
 * FCM `MessagingErrorCode.UNREGISTERED` / `INVALID_ARGUMENT` / `SENDER_ID_MISMATCH` 는 토큰이
 * 영구 무효 — caller 가 user_devices.isActive = false 처리해야 무효 토큰 누적 방지.
 */
sealed class FcmSendResult {
    object Success : FcmSendResult()

    /** FCM 이 토큰을 영구 무효로 보고 — caller MUST deactivateByFcmToken(token). */
    object InvalidToken : FcmSendResult()

    /** 네트워크/quota 등 일시적 실패 — caller 는 재시도 가능, cleanup 금지. */
    data class TransientFailure(val message: String?) : FcmSendResult()
}

/**
 * Multicast 결과 — 어떤 토큰이 invalid 였는지 추출하여 caller 가 cleanup.
 */
data class FcmMulticastResult(
    val successCount: Int,
    val invalidTokens: List<String>,
    val totalSent: Int,
)
