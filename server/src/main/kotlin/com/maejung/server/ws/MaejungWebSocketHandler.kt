package com.maejung.server.ws

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.maejung.server.session.SessionService
import com.maejung.server.token.Principal
import com.maejung.server.token.TokenService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Clock

@Component
class MaejungWebSocketHandler(
    private val tokenService: TokenService,
    private val registry: ConnectionRegistry,
    private val sessionService: SessionService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : TextWebSocketHandler() {

    // 핸드셰이크에서 거절하면 클라이언트가 HTTP 응답만 받아 원인을 구분하기 어려워서, 붙인 뒤 4401로 닫는다
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val principal = tokenService.verify(session.attributes[TokenQueryInterceptor.TOKEN_ATTRIBUTE] as? String)
        if (principal == null) {
            session.close(ConnectionRegistry.UNAUTHORIZED)
            return
        }
        session.attributes[PRINCIPAL_ATTRIBUTE] = principal
        registry.register(principal.key, session)
        sessionService.onConnected(principal)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val principal = session.attributes[PRINCIPAL_ATTRIBUTE] as? Principal ?: return
        val envelope = runCatching { objectMapper.readValue<Envelope>(message.payload) }.getOrNull()
        if (envelope == null) {
            registry.send(
                principal.key,
                Envelope("error", ts = clock.millis(), payload = mapOf("code" to "BAD_MESSAGE", "message" to "JSON을 읽을 수 없습니다")),
            )
            return
        }
        sessionService.handle(principal, envelope)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val principal = session.attributes[PRINCIPAL_ATTRIBUTE] as? Principal ?: return
        registry.unregister(principal.key, session)
    }

    companion object {
        private const val PRINCIPAL_ATTRIBUTE = "maejung.principal"
    }
}
