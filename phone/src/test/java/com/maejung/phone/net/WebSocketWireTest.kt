package com.maejung.phone.net

import com.maejung.protocol.PickupRequest
import com.maejung.protocol.Protocol
import com.maejung.protocol.Received
import com.maejung.protocol.SessionState
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** These tests intentionally use real OkHttp upgrades and frames; main owns execution. */
class WebSocketWireTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var scope: CoroutineScope
    private val transports = mutableListOf<WebSocketTransport>()

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() {
        transports.forEach { it.close() }
        scope.cancel()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    @Test
    fun `real upgrade URL encodes token and protocol frames travel both directions`() = runBlocking {
        val frames = LinkedBlockingQueue<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("invalid JSON")
                webSocket.send("""{"type":"session.state","ts":123,"payload":{"state":"none"}}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) { frames.add(text) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        }))
        val transport = transport()
        val incoming = async(start = CoroutineStart.UNDISPATCHED) { transport.incoming.first() }
        try {
            transport.connect("signed.+/&=? token")
            withTimeout(5_000) { transport.connection.first { it == ConnectionState.CONNECTED } }
            assertEquals(Received(SessionState("none"), null, 123), withTimeout(5_000) { incoming.await() })
            assertTrue(transport.send(PickupRequest("김  민지")))
            val request = assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            assertEquals("/ws", request.requestUrl?.encodedPath)
            assertEquals("signed.+/&=? token", request.requestUrl?.queryParameter("token"))
            val frame = Protocol.json.parseToJsonElement(assertNotNull(frames.poll(2, TimeUnit.SECONDS))).jsonObject
            assertEquals("pickup.request", frame["type"]?.jsonPrimitive?.content)
            assertEquals("김  민지", frame["payload"]?.jsonObject?.get("riderName")?.jsonPrimitive?.content)
            assertTrue((frame["ts"]?.jsonPrimitive?.longOrNull ?: 0) > 0)
            assertFalse(frame.containsKey("sessionId"))
        } finally {
            incoming.cancel()
            transport.close()
        }
    }

    @Test
    fun `actual 4401 and 4409 close handshakes stop automatic retry`() = runBlocking {
        for ((index, code) in listOf(4401, 4409).withIndex()) {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.close(code, "terminal") }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            }))
            val transport = transport()
            transport.connect("token")
            val expected = if (code == 4401) ConnectionState.UNAUTHORIZED else ConnectionState.REPLACED
            withTimeout(5_000) { transport.connection.first { it == expected } }
            assertFalse(transport.send(PickupRequest("민지")))
            delay(1_200)
            assertEquals(index + 1, server.requestCount)
            assertEquals(expected, transport.connection.value)
            transport.close()
        }
    }

    @Test
    fun `failed real upgrade retries and establishes a new socket`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        }))
        val transport = transport()
        transport.connect("token")
        withTimeout(5_000) { transport.connection.first { it == ConnectionState.CONNECTED } }
        assertEquals(2, server.requestCount)
        transport.close()
        assertEquals(ConnectionState.DISCONNECTED, transport.connection.value)
    }

    private fun transport() = WebSocketTransport(server.url("/").toString(), scope, client).also { transports += it }
}
