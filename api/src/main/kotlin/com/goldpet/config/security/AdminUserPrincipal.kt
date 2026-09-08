package com.goldpet.config.security

import com.goldpet.domain.admin.entity.AdminUser
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class AdminUserPrincipal(val adminUser: AdminUser) : UserDetails {
    val id: Long = adminUser.id
    val email: String = adminUser.email

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_${adminUser.role.name}"))
    }

    override fun getPassword(): String = adminUser.passwordHash
    override fun getUsername(): String = adminUser.email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = adminUser.isActive
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = adminUser.isActive
}
