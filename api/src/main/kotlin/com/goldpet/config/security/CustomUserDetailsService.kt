package com.goldpet.config.security

import com.goldpet.domain.user.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        // This method is typically used for form-based login.
        // For OAuth2, we use loadUserById.
        throw UsernameNotFoundException("User not found with username: $username")
    }

    @Transactional(readOnly = true)
    fun loadUserById(id: Long): UserDetails {
        val user = userRepository.findById(id).orElseThrow {
            UsernameNotFoundException("User not found with id: $id")
        }
        return UserPrincipal.create(user)
    }
}
