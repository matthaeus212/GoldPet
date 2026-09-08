package com.goldpet.domain.gamification.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate

/**
 * 그날(KST) 노출할 데일리 미션 묶음 — 전 유저 공통(per-user 아님).
 * `badgeIds`는 회전 선택된 repeatable DAILY 배지 id 목록.
 * 진행도/완료 여부는 user_badges(DAILY cycleKey = mission_date) 읽기 조인으로 계산.
 */
@Entity
@Table(
    name = "daily_mission_set",
    uniqueConstraints = [UniqueConstraint(columnNames = ["mission_date"])]
)
class DailyMissionSet(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "mission_date", nullable = false)
    val missionDate: LocalDate,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "badge_ids", columnDefinition = "jsonb", nullable = false)
    val badgeIds: List<Long>
)
