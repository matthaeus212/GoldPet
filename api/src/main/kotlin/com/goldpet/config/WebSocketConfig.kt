// STOMP WebSocket 엔드포인트/브로커와 인증 인터셉터를 등록하는 설정
package com.goldpet.config

import com.goldpet.config.security.CorsProperties
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val stompAuthChannelInterceptor: StompAuthChannelInterceptor,
    private val corsProperties: CorsProperties,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // EXT-CDX-002 (W1a): origin allowlist 를 REST CORS 와 동일 소스(app.cors.allowed-origins)로.
        // 이전 `setAllowedOriginPatterns("*")` 는 임의 오리진의 WebSocket 연결을 허용했다.
        val origins = corsProperties.allowedOrigins.map { it.trim() }.filter { it.isNotEmpty() }
        check(origins.isNotEmpty()) {
            "app.cors.allowed-origins 미설정 — WebSocket origin allowlist 를 구성할 수 없습니다."
        }
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(*origins.toTypedArray())
            .withSockJS() // Use SockJS for browsers that don't support WebSocket
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.setApplicationDestinationPrefixes("/app") // Prefix for messages from clients to server
        registry.enableSimpleBroker("/topic", "/queue") // Prefix for messages from server to clients
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        // CONNECT 프레임에서 JWT 검증 → Principal 주입 (EXT-CDX-002).
        registration.interceptors(stompAuthChannelInterceptor)
    }
}
