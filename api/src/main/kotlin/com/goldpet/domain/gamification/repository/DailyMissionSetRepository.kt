package com.goldpet.domain.gamification.repository

import com.goldpet.domain.gamification.entity.DailyMissionSet
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyMissionSetRepository : JpaRepository<DailyMissionSet, Long> {
    fun findByMissionDate(missionDate: LocalDate): DailyMissionSet?
    fun existsByMissionDate(missionDate: LocalDate): Boolean
}
