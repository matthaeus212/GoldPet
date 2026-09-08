package com.goldpet.domain.notification.reengagement.service

import com.goldpet.domain.notification.reengagement.entity.ReengagementSend
import com.goldpet.domain.notification.reengagement.repository.ReengagementSendRepository
import com.goldpet.domain.notification.service.FcmPushSender
import com.goldpet.domain.user.repository.UserDeviceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 재참여 넛지 1유저 단위 reserve+발송 (W2c). per-user `REQUIRES_NEW` 로 dedup row 를 즉시 커밋해
 * 배포 재시작 중복 발송을 차단(`reengage:{userId}:{date}`). FCM 송신은 best-effort.
 */
@Component
class ReengagementSender(
    private val reengagementSendRepository: ReengagementSendRepository,
    private val fcmPushSender: FcmPushSender,
    private val userDeviceRepository: UserDeviceRepository
) {
    private val log = LoggerFactory.getLogger(ReengagementSender::class.java)

    /**
     * dedup 예약 후 발송. 이미 오늘 발송됐으면(=dedup row 존재) false 반환하고 아무것도 하지 않음.
     * dedup row 는 발송 전에 같은 tx 에서 저장되며, 송신 실패는 삼켜 dedup 을 유지(중복 방지 우선).
     *
     * @return 새로 발송을 예약했으면 true, 이미 발송됨/토큰 없음이면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reserveAndSend(
        userId: Long,
        tokens: List<String>,
        nudgeType: NudgeType,
        today: LocalDate,
        title: String,
        message: String
    ): Boolean {
        if (tokens.isEmpty()) return false
        if (reengagementSendRepository.existsByUserIdAndSendDate(userId, today)) return false

        // dedup 예약(이 tx 커밋 시 영속). 유니크 제약이 동시 race 의 최종 backstop.
        reengagementSendRepository.save(
            ReengagementSend(userId = userId, sendDate = today, nudgeType = nudgeType.name)
        )

        try {
            val result = fcmPushSender.sendToMultipleDetailed(
                fcmTokens = tokens,
                title = title,
                message = message,
                data = mapOf("type" to "REENGAGEMENT", "nudge" to nudgeType.name)
            )
            result.invalidTokens.forEach { userDeviceRepository.deactivateByFcmToken(it) }
        } catch (e: Exception) {
            // best-effort: 송신 실패해도 dedup 은 유지(중복 발송 방지 우선).
            log.warn("Reengagement push failed (best-effort): userId={} type={}: {}", userId, nudgeType, e.message)
        }
        return true
    }
}
