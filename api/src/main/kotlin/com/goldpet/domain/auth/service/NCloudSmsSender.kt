package com.goldpet.domain.auth.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 네이버 클라우드 SENS 를 통한 실제 SMS 발송.
 *
 * - dev/prod 프로파일에서만 활성화 (local/test/codegen 은 [LoggingSmsSender]).
 * - 모든 키/시크릿/발신번호는 환경변수로 외부화한다(코드·yml 에 값 하드코딩 금지).
 *   설정 누락 시 기동은 되지만 발송 시 명확히 실패하도록 빈 기본값을 둔다.
 */
@Component
@Profile("dev", "prod")
class NCloudSmsSender(
    @Value("\${ncloud.sms.host:https://sens.apigw.ntruss.com}") private val host: String,
    @Value("\${ncloud.sms.access-key:}") private val accessKey: String,
    @Value("\${ncloud.sms.secret-key:}") private val secretKey: String,
    @Value("\${ncloud.sms.service-id:}") private val serviceId: String,
    @Value("\${ncloud.sms.sender-number:}") private val from: String,
) : SmsSender {

    private val log = LoggerFactory.getLogger(javaClass)
    private val http: HttpClient = HttpClient.newHttpClient()

    companion object {
        /**
         * SEC-008 — 로그에 남길 수신 전화번호 마스킹. 앞 3자리 + 뒤 4자리만 남기고
         * 가운데는 `*` 로 가린다(예: 01012345678 → 010****5678). 7자리 이하는 전부 마스킹.
         */
        fun maskPhoneNumber(digits: String): String {
            if (digits.length <= 7) return "*".repeat(digits.length)
            val prefix = digits.take(3)
            val suffix = digits.takeLast(4)
            return prefix + "*".repeat(digits.length - 7) + suffix
        }
    }

    override fun send(phoneNumber: String, message: String) {
        require(accessKey.isNotBlank() && secretKey.isNotBlank() && serviceId.isNotBlank() && from.isNotBlank()) {
            "NCloud SMS 설정이 비어 있습니다. NCLOUD_SMS_* 환경변수를 확인하세요."
        }

        val to = phoneNumber.filter { it.isDigit() }
        val maskedTo = maskPhoneNumber(to)
        val timestamp = System.currentTimeMillis().toString()
        val urlPath = "/sms/v2/services/$serviceId/messages"
        val signature = makeSignature("POST", urlPath, timestamp)

        // 인증 문구는 90byte 이내라 SMS 타입으로 충분(초과 시 LMS 전환 필요).
        val body = """
            {
              "type":"SMS",
              "contentType":"COMM",
              "countryCode":"82",
              "from":"$from",
              "content":${message.toJsonString()},
              "messages":[
                {"to":"$to","content":${message.toJsonString()}}
              ]
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(host + urlPath))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("x-ncp-apigw-timestamp", timestamp)
            .header("x-ncp-iam-access-key", accessKey)
            .header("x-ncp-apigw-signature-v2", signature)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val res: HttpResponse<String> = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 202) {
            // SEC-008: 수신번호(PII)는 마스킹해서 로그. body 는 NCloud API 응답(진단용).
            log.error("[SENS] SMS 발송 실패 status={} body={} to={}", res.statusCode(), res.body(), maskedTo)
            throw IllegalStateException("SMS 발송 실패 (status=${res.statusCode()})")
        }
        log.info("[SENS] SMS 접수 성공 to={}", maskedTo)
    }

    /** NCloud APIGW 서명 v2: "{method} {url}\n{timestamp}\n{accessKey}" 의 HMAC-SHA256 (Base64) */
    private fun makeSignature(method: String, url: String, timestamp: String): String {
        val msg = "$method $url\n$timestamp\n$accessKey"
        val signingKey = SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256").apply { init(signingKey) }
        val rawHmac = mac.doFinal(msg.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(rawHmac)
    }

    /** content 안의 따옴표/개행/역슬래시를 안전하게 JSON 문자열로 escape */
    private fun String.toJsonString(): String {
        val sb = StringBuilder("\"")
        for (c in this) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        return sb.append("\"").toString()
    }
}
