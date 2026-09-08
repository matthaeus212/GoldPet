package com.goldpet.domain.auth.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@Service
class VerificationService(
    private val redisTemplate: StringRedisTemplate,
    private val smsSender: SmsSender
) {
    private val logger = LoggerFactory.getLogger(VerificationService::class.java)
    private val CODE_TTL_MINUTES = 3L

    fun sendVerificationCode(phoneNumber: String): String {
        // Generate 6-digit random code
        val code = Random.nextInt(100000, 999999).toString()

        // Save to Redis
        redisTemplate.opsForValue().set(
            "verify:$phoneNumber",
            code,
            CODE_TTL_MINUTES,
            TimeUnit.MINUTES
        )

        smsSender.send(phoneNumber, "인증번호: $code")
        return code
    }

    fun verifyCode(phoneNumber: String, code: String): Boolean {
        val storedCode = redisTemplate.opsForValue().get("verify:$phoneNumber")
        
        if (storedCode != null && storedCode == code) {
            // Optional: Delete code after successful verification to prevent reuse
            redisTemplate.delete("verify:$phoneNumber")
            return true
        }
        return false
    }
}
