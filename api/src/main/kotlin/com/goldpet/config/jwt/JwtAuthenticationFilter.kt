package com.goldpet.config.jwt

import com.goldpet.config.security.CustomUserDetailsService
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

@Component
class JwtAuthenticationFilter(
    private val tokenProvider: JwtTokenProvider,
    private val customUserDetailsService: CustomUserDetailsService,
    private val adminUserRepository: com.goldpet.domain.admin.repository.AdminUserRepository
) : OncePerRequestFilter() {

    @Throws(IOException::class, ServletException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val jwt = getJwtFromRequest(request)

            if (jwt != null && tokenProvider.validateToken(jwt)) {
                val userId = tokenProvider.getUserIdFromJWT(jwt)
                val userType = tokenProvider.getUserTypeFromJWT(jwt)
                
                val userDetails = if (userType == "ADMIN") {
                    val adminUser = adminUserRepository.findById(userId.toLong()).orElseThrow { Exception("Admin not found") }
                    // Spring Security UsernamePasswordAuthenticationToken는 UserDetails.isEnabled()을 자동 검증하지 않으므로
                    // 비활성화된 admin 계정의 토큰이 재사용되는 것을 막기 위해 여기서 직접 401을 반환한다.
                    if (!adminUser.isActive) {
                        response.status = HttpStatus.UNAUTHORIZED.value()
                        response.contentType = "application/json;charset=UTF-8"
                        response.writer.write("{\"error\":\"ACCOUNT_DEACTIVATED\"}")
                        return
                    }
                    if (adminUser.mustChangePassword) {
                        val allowedPaths = setOf(
                            "/api/v1/admin/auth/change-password",
                            "/api/v1/admin/auth/logout",
                            "/api/v1/admin/auth/me"
                        )
                        if (request.requestURI !in allowedPaths) {
                            response.status = HttpStatus.FORBIDDEN.value()
                            response.contentType = "application/json;charset=UTF-8"
                            response.writer.write("{\"error\":\"PASSWORD_CHANGE_REQUIRED\"}")
                            return
                        }
                    }
                    com.goldpet.config.security.AdminUserPrincipal(adminUser)
                } else {
                    customUserDetailsService.loadUserById(userId.toLong())
                }

                val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                SecurityContextHolder.getContext().authentication = authentication
            }
        } catch (ex: Exception) {
            logger.error("Could not set user authentication in security context", ex)
        }

        filterChain.doFilter(request, response)
    }

    private fun getJwtFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7, bearerToken.length)
        } else null
    }
}
