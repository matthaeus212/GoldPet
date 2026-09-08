package com.goldpet.domain.common.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 프론트엔드 런타임 오류(ErrorBoundary 등)를 서버 로그로 수집하는 경량 엔드포인트.
 * 디바이스에서 WebView 콘솔 접근이 어려운 릴리즈 빌드 대응용.
 */
@Tag(name = "클라이언트 로그", description = "프론트엔드/WebView 런타임 오류 수집 API")
@RestController
@RequestMapping("/api/v1/client-logs")
class ClientLogController {
    private val log = LoggerFactory.getLogger(ClientLogController::class.java)

    @Operation(summary = "클라이언트 오류 로그 수집 (ErrorBoundary / WebView 런타임 오류)")
    @PostMapping("/error")
    fun receiveClientError(
        @RequestBody body: ClientErrorPayload,
        @RequestHeader(value = "User-Agent", required = false) userAgent: String?,
    ): ResponseEntity<Void> {
        log.error(
            "[CLIENT-ERROR] url={} name={} message={} ua={}\nstack={}\ncomponentStack={}\nextra={}",
            body.url,
            body.name,
            body.message,
            userAgent,
            body.stack,
            body.componentStack,
            body.extra,
        )
        return ResponseEntity.noContent().build()
    }
}

data class ClientErrorPayload(
    val url: String? = null,
    val name: String? = null,
    val message: String? = null,
    val stack: String? = null,
    val componentStack: String? = null,
    val extra: Map<String, Any?>? = null,
)
