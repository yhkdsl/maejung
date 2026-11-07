package com.maejung.net

import com.maejung.protocol.ClientMessage
import com.maejung.protocol.Received
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    /** 끊김. 재연결을 시도하는 중이다 */
    DISCONNECTED,
    /** 4401: 토큰 무효. 재연결하지 않는다 */
    UNAUTHORIZED,
    /** 4409: 같은 차의 새 연결이 붙었다. 재연결하지 않는다 */
    REPLACED,
}

/** 서버 WebSocket(docs/PROTOCOL.md 3절). 구현: OkHttp, 서버 없이 쓰는 가짜 */
interface SessionTransport {
    val incoming: Flow<Received>
    val connection: StateFlow<ConnectionState>

    fun connect(token: String)

    fun send(message: ClientMessage)

    fun close()
}
