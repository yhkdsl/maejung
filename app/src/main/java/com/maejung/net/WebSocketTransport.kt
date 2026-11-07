package com.maejung.net

import android.util.Log
import com.maejung.protocol.ApiPaths
import com.maejung.protocol.ClientMessage
import com.maejung.protocol.CloseCodes
import com.maejung.protocol.Protocol
import com.maejung.protocol.Received
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * 서버 WebSocket(docs/PROTOCOL.md 3절).
 * 끊기면 1·2·4·8·15초 간격으로 다시 붙는다. 4401(토큰 무효)·4409(새 연결로 교체)는 다시 붙지 않는다.
 */
class WebSocketTransport(
    private val serverUrl: String,
    private val scope: CoroutineScope,
    private val client: OkHttpClient = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build(),
) : SessionTransport {
    private val _incoming = MutableSharedFlow<Received>(extraBufferCapacity = 64)
    override val incoming: Flow<Received> = _incoming.asSharedFlow()

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private var token: String? = null
    private var socket: WebSocket? = null
    private var retry: Job? = null
    private var attempt = 0
    @Volatile private var closedByUser = false

    override fun connect(token: String) {
        this.token = token
        closedByUser = false
        attempt = 0
        open()
    }

    override fun send(message: ClientMessage) {
        val sent = socket?.send(Protocol.encode(message, ts = System.currentTimeMillis())) ?: false
        if (!sent) Log.w(TAG, "보내지 못함(연결 없음): ${Protocol.typeOf(message)}")
    }

    override fun close() {
        closedByUser = true
        retry?.cancel()
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        _connection.value = ConnectionState.DISCONNECTED
    }

    private fun open() {
        val token = token ?: return
        retry?.cancel()
        _connection.value = ConnectionState.CONNECTING
        val url = serverUrl.replaceFirst("http", "ws") + ApiPaths.WEBSOCKET + "?token=" + token
        socket = client.newWebSocket(Request.Builder().url(url).build(), Listener())
    }

    private fun scheduleReconnect() {
        if (closedByUser) return
        val delayMs = BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.lastIndex)]
        attempt += 1
        _connection.value = ConnectionState.DISCONNECTED
        retry?.cancel()
        retry = scope.launch {
            delay(delayMs)
            open()
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
            _connection.value = ConnectionState.CONNECTED
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val received = Protocol.decode(text)
            if (received == null) Log.w(TAG, "읽을 수 없는 메시지") else _incoming.tryEmit(received)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
            if (webSocket != socket) return
            when (code) {
                CloseCodes.INVALID_TOKEN -> _connection.value = ConnectionState.UNAUTHORIZED
                CloseCodes.REPLACED -> _connection.value = ConnectionState.REPLACED
                else -> scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket != socket) return
            Log.w(TAG, "연결 실패, 다시 붙습니다: ${t.message}")
            scheduleReconnect()
        }
    }

    private companion object {
        const val TAG = "WebSocketTransport"
        const val NORMAL_CLOSURE = 1000
        val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000)
    }
}
