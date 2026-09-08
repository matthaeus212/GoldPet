package com.goldpet.service.oauth2

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class OAuth2AuthenticationFailureHandler(
    @Value("\${app.frontend.url}") private val frontendUrl: String
) : SimpleUrlAuthenticationFailureHandler() {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        var errorMessage = "로그인 중 오류가 발생했습니다."

        if (exception is OAuth2AuthenticationException) {
            when (exception.error.errorCode) {
                "email_already_exists" -> {
                    errorMessage = exception.error.description ?: errorMessage
                }
                else -> {
                    errorMessage = exception.error.description ?: errorMessage
                }
            }
        }

        // Redirect to frontend with error message
        val targetUrl = UriComponentsBuilder.fromUriString("$frontendUrl/login")
            .queryParam("error", URLEncoder.encode(errorMessage, StandardCharsets.UTF_8))
            .build()
            .toUriString()

        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}
