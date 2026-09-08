package com.goldpet.domain.user.repository

import com.goldpet.domain.user.entity.ConsentHistory
import com.goldpet.domain.user.entity.ConsentType
import org.springframework.data.jpa.repository.JpaRepository

interface ConsentHistoryRepository : JpaRepository<ConsentHistory, Long> {
    fun findByUserIdOrderByAgreedAtDesc(userId: Long): List<ConsentHistory>
    fun findByUserIdAndConsentType(userId: Long, consentType: ConsentType): List<ConsentHistory>
    fun findTopByUserIdAndConsentTypeOrderByAgreedAtDesc(userId: Long, consentType: ConsentType): ConsentHistory?
}
