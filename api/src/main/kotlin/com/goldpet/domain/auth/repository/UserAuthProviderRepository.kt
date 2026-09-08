package com.goldpet.domain.auth.repository

import com.goldpet.domain.auth.entity.UserAuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserAuthProviderRepository : JpaRepository<UserAuthProvider, Long> {
    fun findByProviderAndProviderId(provider: String, providerId: String): Optional<UserAuthProvider>
    fun findAllByUserId(userId: Long): List<UserAuthProvider>
    fun deleteAllByUserId(userId: Long)
}
