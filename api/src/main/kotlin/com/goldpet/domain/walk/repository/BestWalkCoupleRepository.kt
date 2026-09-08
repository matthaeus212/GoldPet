package com.goldpet.domain.walk.repository

import com.goldpet.domain.walk.entity.BestWalkCouple
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface BestWalkCoupleRepository : JpaRepository<BestWalkCouple, Long> {
    fun findByYearMonth(yearMonth: String): BestWalkCouple?

    @Transactional
    fun deleteByYearMonth(yearMonth: String): Long
}
