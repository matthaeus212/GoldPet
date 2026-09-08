package com.goldpet.config.security

import com.goldpet.domain.user.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.user.OAuth2User

class UserPrincipal(
    val user: User,
    private val attributes: Map<String, Any>?
) : OAuth2User, UserDetails {

    val id: Long = user.id

    override fun getAttributes(): Map<String, Any>? {
        return attributes
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_USER"))
    }

    override fun getPassword(): String? {
        return user.password
    }

    override fun getUsername(): String {
        return user.email ?: user.oauthId
    }

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = user.isActive

    override fun getName(): String {
        return user.id.toString()
    }

    companion object {
        fun create(user: User): UserPrincipal {
            return UserPrincipal(user, null)
        }

        fun create(user: User, attributes: Map<String, Any>): UserPrincipal {
            return UserPrincipal(user, attributes)
        }
    }
}
