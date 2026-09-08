package com.goldpet.domain.gamification.streak.repository

import com.goldpet.domain.gamification.streak.entity.UserStreak
import org.springframework.data.jpa.repository.JpaRepository

interface UserStreakRepository : JpaRepository<UserStreak, Long> {
    fun findByUserId(userId: Long): UserStreak?
}
