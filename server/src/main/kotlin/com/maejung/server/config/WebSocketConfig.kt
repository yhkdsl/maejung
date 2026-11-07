package com.maejung.server.config

import com.maejung.server.ws.MaejungWebSocketHandler
import com.maejung.server.ws.TokenQueryInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(private val handler: MaejungWebSocketHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // 클라이언트는 앱(OkHttp)뿐이라 Origin 제한이 의미 없다
        registry.addHandler(handler, "/ws")
            .addInterceptors(TokenQueryInterceptor())
            .setAllowedOrigins("*")
    }
}
