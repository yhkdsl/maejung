package com.maejung.phone.net

import com.maejung.protocol.ClientMessage
import com.maejung.protocol.Received
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, UNAUTHORIZED, REPLACED }

interface SessionTransport {
    val incoming: Flow<Received>
    val connection: StateFlow<ConnectionState>
    fun connect(token: String)
    fun send(message: ClientMessage): Boolean
    fun close()
}
