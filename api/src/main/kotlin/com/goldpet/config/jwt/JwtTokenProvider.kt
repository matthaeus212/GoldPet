package com.goldpet.config.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import com.goldpet.config.security.UserPrincipal
import com.goldpet.config.security.AdminUserPrincipal
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${app.jwt.secret}")
    private val jwtSecret: String,

    @Value("\${app.jwt.expiration-ms}")
    private val jwtExpirationMs: Long,

    @Value("\${app.jwt.refresh-expiration-ms:2592000000}")
    private val refreshExpirationMs: Long = 2592000000
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray())

    fun generateToken(authentication: Authentication): String {
        val principal = authentication.principal
        
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)
        
        val builder = Jwts.builder()
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)

        if (principal is UserPrincipal) {
            builder.subject(principal.id.toString())
            builder.claim("type", "USER")
        } else if (principal is AdminUserPrincipal) {
            builder.subject(principal.id.toString())
            builder.claim("type", "ADMIN")
        } else {
            throw IllegalArgumentException("Unknown principal type")
        }

        return builder.compact()
    }

    fun validateToken(authToken: String): Boolean {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(authToken)
            true
        } catch (ex: Exception) {
            false
        }
    }

    fun getUserIdFromJWT(token: String): String {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        return claims.subject
    }
    
    fun getUserTypeFromJWT(token: String): String {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        return claims["type"] as String? ?: "USER" // Default to USER if missing for backward compatibility
    }

    fun generateRefreshToken(userId: Long): String {
        val now = Date()
        val expiryDate = Date(now.time + refreshExpirationMs)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "REFRESH")
            .id(UUID.randomUUID().toString())  // jti — V66 rotation token uniqueness (same userId 동시 발급 충돌 방지)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    /** V66: refresh token expiry parsed as LocalDateTime — RotationService persists this in user_refresh_tokens.expires_at. */
    fun getRefreshExpiry(token: String): LocalDateTime {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        return claims.expiration.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

    fun validateRefreshToken(token: String): Boolean {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            claims["type"] == "REFRESH"
        } catch (ex: Exception) {
            false
        }
    }

    fun getUserIdFromRefreshToken(token: String): Long {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        if (claims["type"] != "REFRESH") {
            throw IllegalArgumentException("Not a refresh token")
        }
        return claims.subject.toLong()
    }

    fun generatePasswordResetToken(username: String, email: String): String {
        val now = Date()
        val expiryDate = Date(now.time + 300_000) // 5 minutes

        return Jwts.builder()
            .subject(username)
            .claim("type", "PASSWORD_RESET")
            .claim("email", email)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    fun validatePasswordResetToken(token: String, username: String, email: String): Boolean {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            claims["type"] == "PASSWORD_RESET" &&
                claims.subject == username &&
                claims["email"] == email
        } catch (ex: Exception) {
            false
        }
    }

    /**
     * 휴면 해제 전용 단기 토큰 (STYLE-001).
     *
     * 휴면 사용자는 로그인이 차단되므로 access token 을 받을 수 없다. 다만 로그인 시도 자체가
     * 자격증명(비밀번호/SNS)을 이미 검증했으므로, 그 시점에만 이 토큰을 발급해 "본인이 맞다"를
     * 짧게 증명한다. 다른 API 에는 쓸 수 없다(type 이 다르므로 인증 필터가 거른다).
     */
    fun generateDormantActivationToken(userId: Long): String {
        val now = Date()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", DORMANT_ACTIVATION_TYPE)
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiration(Date(now.time + DORMANT_ACTIVATION_EXPIRATION_MS))
            .signWith(key)
            .compact()
    }

    /** 휴면 해제 토큰 검증 — 유효하면 userId, 아니면 null. */
    fun parseDormantActivationUserId(token: String): Long? = try {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        if (claims["type"] == DORMANT_ACTIVATION_TYPE) claims.subject.toLong() else null
    } catch (ex: Exception) {
        null
    }

    companion object {
        const val DORMANT_ACTIVATION_TYPE = "DORMANT_ACTIVATION"
        /** 10분. 해제 절차를 마치기엔 충분하고, 유출돼도 창이 짧다. */
        const val DORMANT_ACTIVATION_EXPIRATION_MS = 10 * 60 * 1000L
    }
}
