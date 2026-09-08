package com.goldpet.service.oauth2

import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error

class OAuth2EmailAlreadyExistsException(message: String) : OAuth2AuthenticationException(
    OAuth2Error("email_already_exists", message, null),
    message
)
