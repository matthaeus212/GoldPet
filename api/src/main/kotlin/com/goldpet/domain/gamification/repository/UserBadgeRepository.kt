package com.goldpet.domain.gamification.repository

import com.goldpet.domain.gamification.entity.Badge
import com.goldpet.domain.gamification.entity.UserBadge
import com.goldpet.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserBadgeRepository : JpaRepository<UserBadge, Long> {
    fun findAllByUser(user: User): List<UserBadge>
    fun existsByUserAndBadge(user: User, badge: Badge): Boolean
    fun existsByUserAndBadgeAndCycleKey(user: User, badge: Badge, cycleKey: String?): Boolean
    fun findByUserAndBadgeAndCycleKey(user: User, badge: Badge, cycleKey: String?): UserBadge?
    fun findByUserIdAndCycleKey(userId: Long, cycleKey: String?): List<UserBadge>
    fun countByBadgeId(badgeId: Long): Long

    /**
     * 해당 유저가 주어진 cycleKey(=KST 날짜)에 받은 DAILY 미션 골드 합계.
     * 데일리 미션 골드 일 20 cap 계산에 사용 (V73 / plan §2.3).
     */
    @Query(
        "SELECT COALESCE(SUM(ub.rewardGoldGiven), 0) FROM UserBadge ub " +
            "WHERE ub.user.id = :userId AND ub.cycleKey = :cycleKey AND ub.badge.repeatCycle = 'DAILY'"
    )
    fun sumDailyRewardGoldGiven(userId: Long, cycleKey: String?): Long

    @Query(
        "SELECT badge_id AS badgeId, COUNT(*) AS earnedCount FROM user_badges GROUP BY badge_id",
        nativeQuery = true
    )
    fun findBadgeIdEarnedCounts(): List<BadgeIdEarnedCount>
}

interface BadgeIdEarnedCount {
    fun getBadgeId(): Long
    fun getEarnedCount(): Long
}
