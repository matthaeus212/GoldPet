package com.goldpet.domain.user.service

import com.goldpet.domain.user.entity.ConsentHistory
import com.goldpet.domain.user.entity.ConsentType
import com.goldpet.domain.user.repository.ConsentHistoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ConsentService(
    private val consentHistoryRepository: ConsentHistoryRepository
) {
    @Transactional
    fun recordConsent(
        userId: Long,
        consentType: ConsentType,
        isAgreed: Boolean,
        version: String = "1.0",
        ipAddress: String? = null
    ): ConsentHistory {
        return consentHistoryRepository.save(
            ConsentHistory(
                userId = userId,
                consentType = consentType,
                isAgreed = isAgreed,
                consentVersion = version,
                ipAddress = ipAddress
            )
        )
    }

    @Transactional
    fun recordSignupConsents(
        userId: Long,
        termsAgreed: Boolean,
        privacyAgreed: Boolean,
        marketingAgreed: Boolean,
        locationAgreed: Boolean,
        ipAddress: String? = null
    ) {
        if (termsAgreed) recordConsent(userId, ConsentType.TERMS, true, ipAddress = ipAddress)
        if (privacyAgreed) recordConsent(userId, ConsentType.PRIVACY, true, ipAddress = ipAddress)
        recordConsent(userId, ConsentType.MARKETING, marketingAgreed, ipAddress = ipAddress)
        recordConsent(userId, ConsentType.LOCATION, locationAgreed, ipAddress = ipAddress)
    }

    fun getConsentHistory(userId: Long): List<ConsentHistoryResponse> {
        return consentHistoryRepository.findByUserIdOrderByAgreedAtDesc(userId)
            .map { ConsentHistoryResponse.from(it) }
    }

    fun getLatestConsent(userId: Long, type: ConsentType): ConsentHistoryResponse? {
        return consentHistoryRepository.findTopByUserIdAndConsentTypeOrderByAgreedAtDesc(userId, type)
            ?.let { ConsentHistoryResponse.from(it) }
    }
}

data class ConsentHistoryResponse(
    val id: Long,
    val consentType: String,
    val consentVersion: String,
    val isAgreed: Boolean,
    val agreedAt: String
) {
    companion object {
        fun from(entity: ConsentHistory): ConsentHistoryResponse {
            return ConsentHistoryResponse(
                id = entity.id,
                consentType = entity.consentType.name,
                consentVersion = entity.consentVersion,
                isAgreed = entity.isAgreed,
                agreedAt = entity.agreedAt.toString()
            )
        }
    }
}
