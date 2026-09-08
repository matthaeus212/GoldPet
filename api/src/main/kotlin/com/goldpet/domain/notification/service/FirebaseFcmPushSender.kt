package com.goldpet.domain.notification.service

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Firebase Admin SDK를 사용한 실제 FCM 푸시 전송
 */
@Component
@Profile("local", "dev", "prod")
class FirebaseFcmPushSender : FcmPushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(fcmToken: String, title: String, message: String, data: Map<String, String>): Boolean {
        return try {
            val notification = Notification.builder()
                .setTitle(title)
                .setBody(message)
                .build()

            val fcmMessage = Message.builder()
                .setToken(fcmToken)
                .setNotification(notification)
                .putAllData(data)
                .build()

            val response = FirebaseMessaging.getInstance().send(fcmMessage)
            log.info("[FCM] Push sent successfully: {}", response)
            true
        } catch (e: Exception) {
            log.error("[FCM] Failed to send push to token={}: {}", fcmToken.take(20), e.message)
            false
        }
    }

    override fun sendToMultiple(fcmTokens: List<String>, title: String, message: String, data: Map<String, String>): Int {
        if (fcmTokens.isEmpty()) return 0

        return try {
            val notification = Notification.builder()
                .setTitle(title)
                .setBody(message)
                .build()

            // FCM allows max 500 tokens per multicast
            var successCount = 0
            fcmTokens.chunked(500).forEach { chunk ->
                val multicastMessage = MulticastMessage.builder()
                    .addAllTokens(chunk)
                    .setNotification(notification)
                    .putAllData(data)
                    .build()

                val response = FirebaseMessaging.getInstance().sendEachForMulticast(multicastMessage)
                successCount += response.successCount
                if (response.failureCount > 0) {
                    log.warn("[FCM] {} failures in multicast send", response.failureCount)
                }
            }
            log.info("[FCM] Multicast push sent: {}/{} successful", successCount, fcmTokens.size)
            successCount
        } catch (e: Exception) {
            log.error("[FCM] Failed multicast send: {}", e.message)
            0
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sprint 4 BLOCKER #8 — Invalid token 신호 추출 메서드.
    // FirebaseMessagingException.messagingErrorCode 검사 → caller 가 cleanup.
    // ─────────────────────────────────────────────────────────────────────────

    override fun sendDetailed(fcmToken: String, title: String, message: String, data: Map<String, String>): FcmSendResult {
        return try {
            val fcmMessage = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder().setTitle(title).setBody(message).build())
                .putAllData(data)
                .build()
            FirebaseMessaging.getInstance().send(fcmMessage)
            FcmSendResult.Success
        } catch (e: FirebaseMessagingException) {
            if (isInvalidTokenError(e.messagingErrorCode)) {
                log.warn("[FCM] Invalid token ({}): {}...", e.messagingErrorCode, fcmToken.take(20))
                FcmSendResult.InvalidToken
            } else {
                log.error("[FCM] Transient failure ({}): {}", e.messagingErrorCode, e.message)
                FcmSendResult.TransientFailure(e.message)
            }
        } catch (e: Exception) {
            log.error("[FCM] Unexpected send failure: {}", e.message)
            FcmSendResult.TransientFailure(e.message)
        }
    }

    override fun sendToMultipleDetailed(fcmTokens: List<String>, title: String, message: String, data: Map<String, String>): FcmMulticastResult {
        if (fcmTokens.isEmpty()) return FcmMulticastResult(0, emptyList(), 0)

        val invalidTokens = mutableListOf<String>()
        var successCount = 0

        fcmTokens.chunked(500).forEach { chunk ->
            try {
                val multicast = MulticastMessage.builder()
                    .addAllTokens(chunk)
                    .setNotification(Notification.builder().setTitle(title).setBody(message).build())
                    .putAllData(data)
                    .build()

                val response = FirebaseMessaging.getInstance().sendEachForMulticast(multicast)
                successCount += response.successCount

                response.responses.forEachIndexed { idx, sendResponse ->
                    if (!sendResponse.isSuccessful) {
                        val errorCode = (sendResponse.exception as? FirebaseMessagingException)?.messagingErrorCode
                        if (isInvalidTokenError(errorCode)) {
                            invalidTokens.add(chunk[idx])
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("[FCM] Multicast chunk failed: {}", e.message)
            }
        }

        if (invalidTokens.isNotEmpty()) {
            log.warn("[FCM] Multicast: {} invalid tokens (caller MUST cleanup)", invalidTokens.size)
        }
        return FcmMulticastResult(successCount, invalidTokens, fcmTokens.size)
    }

    private fun isInvalidTokenError(errorCode: MessagingErrorCode?): Boolean = errorCode in INVALID_TOKEN_ERROR_CODES

    companion object {
        /** 토큰을 영구 무효로 간주해야 하는 FCM error code 화이트리스트. */
        private val INVALID_TOKEN_ERROR_CODES = setOf(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH,
        )
    }
}
