package com.maejung.server.ws

import com.fasterxml.jackson.databind.ObjectMapper
import com.maejung.server.session.Outbox
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import java.util.concurrent.ConcurrentHashMap

/** principal key → 연결 하나. 같은 주인이 새로 붙으면 이전 연결을 4409로 닫는다 */
@Component
class ConnectionRegistry(private val objectMapper: ObjectMapper) : Outbox {
    private val log = LoggerFactory.getLogger(javaClass)
    private val connections = ConcurrentHashMap<String, WebSocketSession>()

    fun register(principalKey: String, session: WebSocketSession) {
        // sendMessage는 스레드 안전하지 않아서 감싸서 등록한다
        val decorated = ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT)
        connections.put(principalKey, decorated)?.let { previous ->
            runCatching { previous.close(REPLACED) }
        }
    }

    fun unregister(principalKey: String, session: WebSocketSession) {
        connections.computeIfPresent(principalKey) { _, current ->
            val same = current.id == session.id
            if (same) null else current
        }
    }

    override fun send(principalKey: String, envelope: Envelope) {
        val session = connections[principalKey] ?: return
        runCatching { session.sendMessage(TextMessage(objectMapper.writeValueAsString(envelope))) }
            .onFailure { log.warn("send failed type={} to={}", envelope.type, principalKey.take(2)) }
    }

    override fun isOnline(principalKey: String): Boolean = connections[principalKey]?.isOpen == true

    companion object {
        val UNAUTHORIZED = CloseStatus(4401, "invalid token")
        val REPLACED = CloseStatus(4409, "replaced by new connection")
        private const val SEND_TIME_LIMIT_MS = 5_000
        private const val BUFFER_SIZE_LIMIT = 64 * 1024
    }
}
