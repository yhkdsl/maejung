package com.maejung.phone.net

import com.maejung.protocol.ApiPaths
import com.maejung.protocol.ClientMessage
import com.maejung.protocol.CloseCodes
import com.maejung.protocol.Protocol
import com.maejung.protocol.Received
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** One app-owned connection; all socket and retry transitions share the same lock. */
class WebSocketTransport internal constructor(
    serverUrl: String,
    private val scope: CoroutineScope,
    private val factory: WebSocket.Factory,
) : SessionTransport {
    constructor(serverUrl: String, scope: CoroutineScope, client: OkHttpClient = OkHttpClient()) :
        this(serverUrl, scope, client.newBuilder().pingInterval(15, TimeUnit.SECONDS).build() as WebSocket.Factory)

    private val lock = Any()
    private val endpoint = serverUrl.toHttpUrl().newBuilder()
        .encodedPath(ApiPaths.WEBSOCKET).query(null).fragment(null).build()
    private val events = Channel<Incoming>(Channel.UNLIMITED)
    private val mutableConnection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connection: StateFlow<ConnectionState> = mutableConnection.asStateFlow()

    // The manager is the sole consumer. Queue the first session.state even when OkHttp
    // delivers it before collection starts, without replaying it to a new collector.
    override val incoming: Flow<Received> = events.receiveAsFlow()
        .filter { event -> synchronized(lock) { isCurrent(event.attempt) } }
        .map { it.received }

    private var generation = 0L
    private var token: String? = null
    private var current: Attempt? = null
    private var retry: Job? = null
    private var failures = 0

    override fun connect(token: String) = synchronized(lock) {
        require(token.isNotBlank()) { "A rider token is required" }
        generation++
        retry?.cancel()
        retry = null
        val old = current
        current = null
        this.token = token
        failures = 0
        old?.socket?.cancel()
        open()
    }

    override fun send(message: ClientMessage): Boolean = synchronized(lock) {
        val attempt = current ?: return@synchronized false
        if (!attempt.opened || mutableConnection.value != ConnectionState.CONNECTED) return@synchronized false
        runCatching { attempt.socket?.send(Protocol.encode(message, System.currentTimeMillis())) ?: false }
            .getOrDefault(false)
    }

    override fun close() = synchronized(lock) {
        generation++
        token = null
        retry?.cancel()
        retry = null
        val old = current
        current = null
        mutableConnection.value = ConnectionState.DISCONNECTED
        if (old?.opened == true) {
            if (old.socket?.close(1000, null) == false) old.socket?.cancel()
        } else {
            old?.socket?.cancel()
        }
        Unit
    }

    /** Called with lock held, before any concurrent callback can inspect installation. */
    private fun open() {
        val token = token ?: return
        val attempt = Attempt(generation)
        current = attempt
        mutableConnection.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(endpoint.newBuilder().addQueryParameter("token", token).build()).build()
        try {
            attempt.socket = factory.newWebSocket(request, Listener(attempt))
            attempt.installing = false
            // A synchronous factory callback waits for the returned socket's identity.
            val pending = attempt.pending.toList()
            attempt.pending.clear()
            pending.forEach { it() }
        } catch (_: RuntimeException) {
            attempt.installing = false
            attempt.pending.clear()
            if (isCurrent(attempt)) finish(attempt, null)
        }
    }

    private fun isCurrent(attempt: Attempt): Boolean =
        token != null && generation == attempt.generation && current === attempt

    private fun callback(attempt: Attempt, socket: WebSocket, action: () -> Unit) {
        synchronized(lock) {
            if (!isCurrent(attempt)) return
            if (attempt.installing) {
                attempt.pending += { callback(attempt, socket, action) }
            } else if (attempt.socket === socket) {
                action()
            }
        }
    }

    private fun finish(attempt: Attempt, code: Int?) {
        if (!isCurrent(attempt)) return
        // Detach before acknowledging a close or launching retry, so a second terminal
        // callback from this socket cannot change state or schedule another retry.
        current = null
        when (code) {
            CloseCodes.INVALID_TOKEN, CloseCodes.REPLACED -> {
                token = null
                retry?.cancel()
                retry = null
                mutableConnection.value = if (code == CloseCodes.INVALID_TOKEN) {
                    ConnectionState.UNAUTHORIZED
                } else {
                    ConnectionState.REPLACED
                }
            }
            else -> {
                mutableConnection.value = ConnectionState.DISCONNECTED
                scheduleRetry()
            }
        }
    }

    private fun scheduleRetry() {
        if (token == null || retry != null) return
        val expectedGeneration = generation
        val delayMillis = BACKOFF[failures.coerceAtMost(BACKOFF.lastIndex)]
        failures = (failures + 1).coerceAtMost(BACKOFF.lastIndex)
        lateinit var scheduled: Job
        scheduled = scope.launch(start = CoroutineStart.LAZY) {
            delay(delayMillis)
            synchronized(lock) {
                if (generation == expectedGeneration && token != null && current == null && retry === scheduled) {
                    retry = null
                    open()
                }
            }
        }
        retry = scheduled
        scheduled.start()
    }

    private inner class Listener(private val attempt: Attempt) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = callback(attempt, webSocket) {
            attempt.opened = true
            failures = 0
            mutableConnection.value = ConnectionState.CONNECTED
        }

        override fun onMessage(webSocket: WebSocket, text: String) = callback(attempt, webSocket) {
            Protocol.decode(text)?.let { events.trySend(Incoming(attempt, it)) }
            Unit
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = callback(attempt, webSocket) {
            finish(attempt, code)
            webSocket.close(code, null)
            Unit
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = callback(attempt, webSocket) {
            finish(attempt, code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = callback(attempt, webSocket) {
            finish(attempt, null)
        }
    }

    private class Attempt(val generation: Long) {
        var socket: WebSocket? = null
        var opened = false
        var installing = true
        val pending = mutableListOf<() -> Unit>()
    }

    private data class Incoming(val attempt: Attempt, val received: Received)

    private companion object {
        val BACKOFF = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000)
    }
}
