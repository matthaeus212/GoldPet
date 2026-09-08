package com.goldpet.domain.notification.reengagement.service

import com.goldpet.domain.common.service.SystemSettingService
import com.goldpet.domain.notification.reengagement.repository.ReengagementSendRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 재참여 넛지 종류. (user_id, send_date) 유니크로 종류 무관 하루 1회 캡. */
enum class NudgeType { DORMANCY, STREAK_AT_RISK }

/**
 * 재참여 푸시 잡 (W2c, plan §2.4).
 *
 * - **트리거**: 휴면-복귀 넛지(18:00 KST, dormancy) + 스트릭-위기 넛지(20:00 KST, current_streak>0 & 오늘 무활동).
 * - **콰이엇아워**: 21:00–09:00 KST 발송 억제(고정값). cron 시각은 콰이엇아워 밖이나 방어적으로 가드.
 * - **동의**: 후보 쿼리에서 `is_reengagement_alert_enabled` 필터 → WALK else->true 상시 레인 미사용.
 * - **멱등**: per-user dedup row(`reengage:{userId}:{date}`) + 하루 1회 캡 → 재시작 중복 차단.
 * - **스케일**: 후보 userId 배치 페이징 + `sendToMultipleDetailed` (per-user createNotification 루프 금지).
 * - **콜드스타트**: 스트릭 쿼리는 user_streaks JOIN → row 부재 유저 자연 제외(tolerant).
 *
 * cron 시각은 기존 03:00–04:40 혼잡창 및 데일리미션 00:05 회피.
 */
@Service
class ReengagementService(
    private val reengagementSendRepository: ReengagementSendRepository,
    private val reengagementSender: ReengagementSender,
    private val systemSettingService: SystemSettingService,
    private val clock: Clock = Clock.system(KST)
) {
    private val log = LoggerFactory.getLogger(ReengagementService::class.java)

    /** 휴면-복귀 넛지 — 18:00 KST. */
    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "reengagementDormancyNudge", lockAtMostFor = "15m", lockAtLeastFor = "1m")
    fun runDormancyNudge() {
        // ShedLock은 primitive(Int) 반환 메서드를 잠글 수 없어 Unit 반환으로 고정.
        runNudge(NudgeType.DORMANCY)
    }

    /** 스트릭-위기 넛지 — 20:00 KST. */
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "reengagementStreakAtRiskNudge", lockAtMostFor = "15m", lockAtLeastFor = "1m")
    fun runStreakAtRiskNudge() {
        runNudge(NudgeType.STREAK_AT_RISK)
    }

    fun runNudge(type: NudgeType): Int {
        val now = LocalDateTime.now(clock)
        if (isQuietHours(now.toLocalTime())) {
            log.info("Reengagement {} suppressed by quiet hours at {} KST", type, now.toLocalTime())
            return 0
        }
        val today = now.toLocalDate()
        val limit = systemSettingService.getInt("reengage.batch.max", 5000)

        val candidates = when (type) {
            NudgeType.DORMANCY -> {
                val dormancyDays = systemSettingService.getInt("reengage.dormancy.days", 3).toLong()
                val maxDays = systemSettingService.getInt("reengage.dormancy.max.days", 14).toLong()
                reengagementSendRepository.findDormancyCandidateUserIds(
                    dormantSince = today.minusDays(dormancyDays).atStartOfDay(),
                    churnFloor = today.minusDays(maxDays).atStartOfDay(),
                    today = today,
                    limit = limit
                )
            }
            NudgeType.STREAK_AT_RISK ->
                reengagementSendRepository.findStreakAtRiskCandidateUserIds(today = today, limit = limit)
        }
        if (candidates.isEmpty()) {
            log.info("Reengagement {}: no candidates", type)
            return 0
        }

        val (title, message) = messageFor(type)
        var sent = 0
        candidates.chunked(TOKEN_BATCH_SIZE).forEach { page ->
            val tokensByUser: Map<Long, List<String>> = reengagementSendRepository.findActiveFcmTokens(page)
                .groupBy({ (it[0] as Number).toLong() }, { it[1] as String })
            page.forEach { userId ->
                val tokens = tokensByUser[userId]?.distinct().orEmpty()
                val didSend = try {
                    reengagementSender.reserveAndSend(userId, tokens, type, today, title, message)
                } catch (e: Exception) {
                    // dedup 동시 race(유니크 위반) 등 → 해당 유저 스킵.
                    log.debug("Reengagement reserve skipped userId={} type={}: {}", userId, type, e.message)
                    false
                }
                if (didSend) sent++
            }
        }
        log.info("Reengagement {} done: candidates={}, sent={}", type, candidates.size, sent)
        return sent
    }

    /** 21:00–09:00 KST 콰이엇아워. */
    fun isQuietHours(time: LocalTime): Boolean {
        val hour = time.hour
        return hour >= QUIET_START_HOUR || hour < QUIET_END_HOUR
    }

    private fun messageFor(type: NudgeType): Pair<String, String> = when (type) {
        NudgeType.DORMANCY ->
            "산책하러 가요 🐕" to "오랜만이에요! 오늘 반려견과 가볍게 산책 한번 어때요? 🐾"
        NudgeType.STREAK_AT_RISK ->
            "연속 산책이 곧 끊겨요 🔥" to "오늘 산책하면 연속 기록이 이어져요. 잊지 마세요!"
    }

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        const val QUIET_START_HOUR = 21 // 21:00 부터 억제
        const val QUIET_END_HOUR = 9    // 09:00 부터 발송 허용
        const val TOKEN_BATCH_SIZE = 100
    }
}
