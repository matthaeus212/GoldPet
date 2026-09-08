package com.goldpet.domain.user.repository

import com.goldpet.domain.user.entity.Hobby
import org.springframework.data.jpa.repository.JpaRepository

interface HobbyRepository : JpaRepository<Hobby, Long> {
    fun findAllByOrderByOrderIndexAsc(): List<Hobby>
    fun findByNameIn(names: List<String>): List<Hobby>
}
