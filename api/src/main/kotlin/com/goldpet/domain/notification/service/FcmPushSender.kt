package com.goldpet.domain.notification.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * FCM 푸시 알림 전송 인터페이스
 *
 * - [send] / [sendToMultiple]: backward compat (Boolean/Int 반환).
 * - [sendDetailed] / [sendToMultipleDetailed]: Sprint 4 BLOCKER #8 — invalid token 신호 추출 후 caller 가 cleanup.
 */
interface FcmPushSender {
    fun send(fcmToken: String, title: String, message: String, data: Map<String, String> = emptyMap()): Boolean
    fun sendToMultiple(fcmTokens: List<String>, title: String, message: String, data: Map<String, String> = emptyMap()): Int

    /** V70 BLOCKER #8 — 토큰 영구 무효 신호 추출 (UNREGISTERED/INVALID_ARGUMENT/SENDER_ID_MISMATCH). */
    fun sendDetailed(fcmToken: String, title: String, message: String, data: Map<String, String> = emptyMap()): FcmSendResult

    /** V70 BLOCKER #8 — multicast 응답에서 invalid 토큰 리스트 추출. */
    fun sendToMultipleDetailed(fcmTokens: List<String>, title: String, message: String, data: Map<String, String> = emptyMap()): FcmMulticastResult
}

/**
 * 로컬/개발/테스트 환경용 FCM 로깅 구현체
 */
@Component
@Profile("test", "codegen")
class LoggingFcmPushSender : FcmPushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(fcmToken: String, title: String, message: String, data: Map<String, String>): Boolean {
        log.info("[FCM-MOCK] Sending push to token={}, title={}, message={}, data={}",
            fcmToken.take(20) + "...", title, message, data)
        return true
    }

    override fun sendToMultiple(fcmTokens: List<String>, title: String, message: String, data: Map<String, String>): Int {
        log.info("[FCM-MOCK] Sending push to {} devices, title={}, message={}", fcmTokens.size, title, message)
        return fcmTokens.size
    }

    override fun sendDetailed(fcmToken: String, title: String, message: String, data: Map<String, String>): FcmSendResult {
        log.info("[FCM-MOCK] sendDetailed token={}, title={}", fcmToken.take(20), title)
        return FcmSendResult.Success
    }

    override fun sendToMultipleDetailed(fcmTokens: List<String>, title: String, message: String, data: Map<String, String>): FcmMulticastResult {
        log.info("[FCM-MOCK] sendToMultipleDetailed {} tokens, title={}", fcmTokens.size, title)
        return FcmMulticastResult(fcmTokens.size, emptyList(), fcmTokens.size)
    }
}
