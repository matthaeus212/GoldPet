package com.goldpet.domain.aiprofile.repository

import com.goldpet.domain.aiprofile.entity.AILoadingTip
import org.springframework.data.jpa.repository.JpaRepository

interface AILoadingTipRepository : JpaRepository<AILoadingTip, Int> {
    fun findAllByIsActiveTrueOrderByDisplayOrderAsc(): List<AILoadingTip>
    fun findAllByOrderByDisplayOrderAsc(): List<AILoadingTip>
}
