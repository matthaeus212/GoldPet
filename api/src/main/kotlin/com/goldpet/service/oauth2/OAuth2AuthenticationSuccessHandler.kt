package com.goldpet.service.oauth2

import com.goldpet.config.jwt.JwtTokenProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Value

@Component
class OAuth2AuthenticationSuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${app.frontend.url}") private val frontendUrl: String
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val token = jwtTokenProvider.generateToken(authentication)
        val principal = authentication.principal as com.goldpet.config.security.UserPrincipal
        val refreshToken = jwtTokenProvider.generateRefreshToken(principal.id)
        redirectStrategy.sendRedirect(request, response,
            "$frontendUrl/loginSuccess?token=$token&refreshToken=$refreshToken")
    }
}
