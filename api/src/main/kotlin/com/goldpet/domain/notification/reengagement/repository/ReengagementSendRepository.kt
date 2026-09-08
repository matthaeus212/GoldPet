package com.goldpet.domain.notification.reengagement.repository

import com.goldpet.domain.notification.reengagement.entity.ReengagementSend
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

interface ReengagementSendRepository : JpaRepository<ReengagementSend, Long> {

    /** 하루 1회 캡 dedup 체크. */
    fun existsByUserIdAndSendDate(userId: Long, sendDate: LocalDate): Boolean

    /**
     * 휴면-복귀 후보: 동의+활성+FCM토큰 보유, 과거 산책 이력은 있으나 최근 [dormantSince] 이후 무산책,
     * 단 [churnFloor] 이후 활동 이력은 있어 완전 이탈은 제외. 오늘 이미 발송된 유저는 제외.
     */
    @Query(
        value = """
            SELECT u.id
            FROM users u
            WHERE u.is_active = true
              AND u.status = 'ACTIVE'
              AND u.is_notification_enabled = true
              AND u.is_reengagement_alert_enabled = true
              AND EXISTS (SELECT 1 FROM user_devices d WHERE d.user_id = u.id AND d.is_active = true AND d.fcm_token IS NOT NULL)
              AND EXISTS (SELECT 1 FROM walks w WHERE w.user_id = u.id AND w.start_time >= :churnFloor)
              AND NOT EXISTS (SELECT 1 FROM walks w WHERE w.user_id = u.id AND w.start_time >= :dormantSince)
              AND NOT EXISTS (SELECT 1 FROM reengagement_sends rs WHERE rs.user_id = u.id AND rs.send_date = :today)
            ORDER BY u.id
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findDormancyCandidateUserIds(
        @Param("dormantSince") dormantSince: LocalDateTime,
        @Param("churnFloor") churnFloor: LocalDateTime,
        @Param("today") today: LocalDate,
        @Param("limit") limit: Int
    ): List<Long>

    /**
     * 스트릭-위기 후보: 동의+활성+FCM토큰 보유, current_streak>0 이며 오늘([today]) 활동 없음.
     * user_streaks row 부재 유저는 JOIN 으로 자연 제외(콜드스타트 tolerant). 오늘 이미 발송된 유저 제외.
     */
    @Query(
        value = """
            SELECT u.id
            FROM users u
            JOIN user_streaks us ON us.user_id = u.id
            WHERE u.is_active = true
              AND u.status = 'ACTIVE'
              AND u.is_notification_enabled = true
              AND u.is_reengagement_alert_enabled = true
              AND EXISTS (SELECT 1 FROM user_devices d WHERE d.user_id = u.id AND d.is_active = true AND d.fcm_token IS NOT NULL)
              AND us.current_streak > 0
              AND us.last_active_date < :today
              AND NOT EXISTS (SELECT 1 FROM reengagement_sends rs WHERE rs.user_id = u.id AND rs.send_date = :today)
            ORDER BY u.id
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findStreakAtRiskCandidateUserIds(
        @Param("today") today: LocalDate,
        @Param("limit") limit: Int
    ): List<Long>

    /** 페이지의 유저들에 대한 활성 FCM 토큰 배치 조회 — (user_id, fcm_token) 튜플. */
    @Query(
        value = """
            SELECT d.user_id, d.fcm_token
            FROM user_devices d
            WHERE d.user_id IN (:userIds) AND d.is_active = true AND d.fcm_token IS NOT NULL
        """,
        nativeQuery = true
    )
    fun findActiveFcmTokens(@Param("userIds") userIds: List<Long>): List<Array<Any>>
}
