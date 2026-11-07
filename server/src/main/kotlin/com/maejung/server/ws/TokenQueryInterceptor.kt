package com.maejung.server.ws

import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder

/** `/ws?token=…`의 토큰을 세션 속성으로 옮기기만 한다. 검증은 핸들러가 한다 */
class TokenQueryInterceptor : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        UriComponentsBuilder.fromUri(request.uri).build().queryParams.getFirst("token")
            ?.let { attributes[TOKEN_ATTRIBUTE] = it }
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit

    companion object {
        const val TOKEN_ATTRIBUTE = "maejung.token"
    }
}
