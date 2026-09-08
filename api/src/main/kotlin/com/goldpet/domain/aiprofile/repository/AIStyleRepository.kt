package com.goldpet.domain.aiprofile.repository

import com.goldpet.domain.aiprofile.entity.AIStyle
import org.springframework.data.jpa.repository.JpaRepository

interface AIStyleRepository : JpaRepository<AIStyle, String> {
    fun findAllByIsActiveTrueOrderByDisplayOrderAsc(): List<AIStyle>
    fun findAllByOrderByDisplayOrderAsc(): List<AIStyle>
}
