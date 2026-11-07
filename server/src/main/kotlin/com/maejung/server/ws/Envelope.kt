package com.maejung.server.ws

import com.fasterxml.jackson.annotation.JsonInclude

/** WebSocket 메시지 공통 형식(PROTOCOL.md 3절). payload 필드는 type마다 다르다 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Envelope(
    val type: String,
    val sessionId: String? = null,
    val ts: Long = 0,
    val payload: Map<String, Any?> = emptyMap(),
)
