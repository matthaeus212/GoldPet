package com.goldpet.domain.gamification.streak.entity

import com.goldpet.domain.user.entity.User
import jakarta.persistence.*
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * WALK 연속 산책 스트릭 (W2a, plan §2.2).
 *
 * - lazy-create: 출시 후 첫 적격 산책 시 row 생성. 과거 산책 backfill 없음(없으면 streak 0).
 * - "하루" 경계는 [com.goldpet.domain.gamification.streak.service.StreakService] 에서
 *   `ZoneId.of("Asia/Seoul")` 로 명시 계산 → [lastActiveDate] 는 KST DATE.
 * - [freezeCount]: 주 1회(ISO week) 무료 프리즈. 1일 공백을 메워 스트릭 유지.
 */
@Entity
@Table(name = "user_streaks")
@EntityListeners(AuditingEntityListener::class)
class UserStreak(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,

    @Column(name = "current_streak", nullable = false)
    var currentStreak: Int = 0,

    @Column(name = "longest_streak", nullable = false)
    var longestStreak: Int = 0,

    @Column(name = "last_active_date")
    var lastActiveDate: LocalDate? = null,

    @Column(name = "freeze_count", nullable = false)
    var freezeCount: Int = 0,

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
