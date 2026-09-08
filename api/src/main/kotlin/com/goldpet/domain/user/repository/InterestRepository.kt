package com.goldpet.domain.user.repository

import com.goldpet.domain.user.entity.Interest
import org.springframework.data.jpa.repository.JpaRepository

interface InterestRepository : JpaRepository<Interest, Long> {
    fun findAllByOrderByOrderIndexAsc(): List<Interest>
    fun findByNameIn(names: List<String>): List<Interest>
}
