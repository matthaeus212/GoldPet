package com.goldpet.domain.admin.service

import com.warrenstrange.googleauth.GoogleAuthenticator
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig
import com.warrenstrange.googleauth.GoogleAuthenticatorKey
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator
import org.springframework.stereotype.Service

@Service
class AdminTotpService {

    private val gAuth = GoogleAuthenticator(
        GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
            .setTimeStepSizeInMillis(30000)
            .setWindowSize(10) // Allow 10 steps (5 minutes) of drift
            .build()
    )

    /**
     * Generate a new random secret key for a user.
     */
    fun generateSecret(): String {
        val key: GoogleAuthenticatorKey = gAuth.createCredentials()
        return key.key
    }

    /**
     * Generate a QR Code URL for Google Authenticator.
     * format: otpauth://totp/GoldPet:admin@goldpet.com?secret=SECRET&issuer=GoldPet
     */
    fun getQrCodeUrl(secret: String, accountName: String): String {
        val credentials = GoogleAuthenticatorKey.Builder(secret).build()
        return GoogleAuthenticatorQRGenerator.getOtpAuthURL("GoldPet", accountName, credentials)
    }

    /**
     * Validate a 6-digit code against the secret.
     */
    fun validateCode(secret: String, code: Int): Boolean {
        return gAuth.authorize(secret, code)
    }
}
