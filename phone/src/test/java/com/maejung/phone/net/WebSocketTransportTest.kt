package com.maejung.phone.net

import com.maejung.protocol.PickupPending
import com.maejung.protocol.PickupRequest
import com.maejung.protocol.Received
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol as HttpProtocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketTransportTest {
    @Test
    fun `first state survives callbacks before factory returns and before collection starts`() = runTest {
        val factory = object : WebSocket.Factory {
            override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
                val socket = TestSocket(request)
                listener.onOpen(socket, response(request))
                listener.onMessage(socket, PENDING)
                return socket
            }
        }
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        transport.connect("token")
        assertEquals(ConnectionState.CONNECTED, transport.connection.value)
        assertEquals(Received(PickupPending, "s_one", 123), transport.incoming.first())
        transport.close()
    }

    @Test
    fun `queued old generation messages cannot reach a later collector`() = runTest {
        val factory = SocketFactory()
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        transport.connect("old")
        factory.open(0)
        factory.message(0, PENDING)
        transport.connect("new")
        factory.open(1)
        factory.message(1, PENDING.replace("s_one", "s_two"))
        assertEquals("s_two", transport.incoming.first().sessionId)
        transport.close()
    }

    @Test
    fun `send is refused until the current socket is open and after close`() = runTest {
        val factory = SocketFactory()
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        assertFalse(transport.send(PickupRequest("민지")))
        transport.connect("token")
        assertEquals(ConnectionState.CONNECTING, transport.connection.value)
        assertFalse(transport.send(PickupRequest("민지")))
        factory.open(0)
        assertTrue(transport.send(PickupRequest("민지")))
        factory.sockets[0].acceptSend = false
        assertFalse(transport.send(PickupRequest("민지")))
        transport.close()
        assertFalse(transport.send(PickupRequest("민지")))
    }

    @Test
    fun `failed handshakes back off one two four eight then fifteen seconds`() = runTest {
        val factory = SocketFactory()
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        transport.connect("token")
        assertEquals(1, factory.sockets.size)
        for ((index, delay) in listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 15_000L).withIndex()) {
            factory.fail(index)
            assertEquals(ConnectionState.DISCONNECTED, transport.connection.value)
            runCurrent()
            advanceTimeBy(delay - 1)
            runCurrent()
            assertEquals(index + 1, factory.sockets.size)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(index + 2, factory.sockets.size)
        }
        transport.close()
    }

    @Test
    fun `successful reconnect resets the next retry to one second`() = runTest {
        val factory = SocketFactory()
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        transport.connect("token")
        factory.fail(0)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        factory.open(1)
        assertEquals(ConnectionState.CONNECTED, transport.connection.value)
        factory.fail(1)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, factory.sockets.size)
        transport.close()
    }

    @Test
    fun `closing closed and failure callbacks schedule only one retry`() = runTest {
        val factory = SocketFactory()
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        transport.connect("token")
        factory.open(0)
        factory.closing(0, 1001)
        factory.fail(0)
        factory.closed(0, 1001)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, factory.sockets.size)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(2, factory.sockets.size)
        transport.close()
    }

    @Test
    fun `terminal close codes survive later callbacks and never retry`() = runTest {
        for ((code, expected) in listOf(4401 to ConnectionState.UNAUTHORIZED, 4409 to ConnectionState.REPLACED)) {
            val factory = SocketFactory()
            val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
            transport.connect("token")
            factory.open(0)
            factory.closing(0, code)
            factory.fail(0)
            factory.closed(0, code)
            factory.open(0)
            assertEquals(expected, transport.connection.value)
            assertFalse(transport.send(PickupRequest("민지")))
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, factory.sockets.size)
            transport.close()
        }
    }

    @Test
    fun `onClosed alone also recognizes a terminal code`() = runTest {
        val factory = SocketFactory()
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        transport.connect("token")
        factory.closed(0, 4409)
        assertEquals(ConnectionState.REPLACED, transport.connection.value)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, factory.sockets.size)
    }

    @Test
    fun `explicit close cancels a scheduled retry and rejects every late callback`() = runTest {
        val factory = SocketFactory()
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        val received = mutableListOf<Received>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { transport.incoming.toList(received) }
        transport.connect("token")
        factory.fail(0)
        runCurrent()
        transport.close()
        factory.open(0)
        factory.message(0, PENDING)
        factory.closing(0, 4401)
        factory.closed(0, 4409)
        factory.fail(0)
        assertEquals(ConnectionState.DISCONNECTED, transport.connection.value)
        assertTrue(received.isEmpty())
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, factory.sockets.size)
    }

    @Test
    fun `new connect ignores all callbacks from the superseded socket`() = runTest {
        val factory = SocketFactory()
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        val received = mutableListOf<Received>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { transport.incoming.toList(received) }
        transport.connect("old")
        transport.connect("new")
        assertEquals(2, factory.sockets.size)
        factory.open(1)
        factory.open(0)
        factory.message(0, PENDING)
        factory.closing(0, 4401)
        factory.closed(0, 4409)
        factory.fail(0)
        assertEquals(ConnectionState.CONNECTED, transport.connection.value)
        assertTrue(received.isEmpty())
        factory.message(1, "not JSON")
        factory.message(1, PENDING)
        assertEquals(listOf(Received(PickupPending, "s_one", 123)), received)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, factory.sockets.size)
        transport.close()
    }

    @Test
    fun `listener also rejects mismatched socket identity in its current generation`() = runTest {
        val factory = SocketFactory()
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        val received = mutableListOf<Received>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { transport.incoming.toList(received) }
        transport.connect("token")
        val impostor = TestSocket(Request.Builder().url("https://example.com/ws").build())
        val listener = factory.listeners.single()
        listener.onOpen(impostor, response(impostor.request()))
        listener.onMessage(impostor, PENDING)
        listener.onClosing(impostor, 4401, "invalid")
        listener.onClosed(impostor, 4409, "replaced")
        listener.onFailure(impostor, IOException("late"), null)
        assertEquals(ConnectionState.CONNECTING, transport.connection.value)
        assertTrue(received.isEmpty())
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, factory.sockets.size)
        factory.open(0)
        assertEquals(ConnectionState.CONNECTED, transport.connection.value)
        transport.close()
    }

    @Test
    fun `new connect cancels the old token retry`() = runTest {
        val factory = SocketFactory()
        val transport = WebSocketTransport("https://example.com", backgroundScope, factory)
        transport.connect("old")
        factory.fail(0)
        runCurrent()
        transport.connect("new")
        factory.open(1)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, factory.sockets.size)
        assertEquals("new", factory.sockets.last().request().url.queryParameter("token"))
        transport.close()
    }

    private class SocketFactory : WebSocket.Factory {
        val sockets = mutableListOf<TestSocket>()
        val listeners = mutableListOf<WebSocketListener>()
        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
            TestSocket(request).also { sockets += it; listeners += listener }
        fun open(index: Int) = listeners[index].onOpen(sockets[index], response(sockets[index].request()))
        fun fail(index: Int) = listeners[index].onFailure(sockets[index], IOException("connection lost"), null)
        fun closing(index: Int, code: Int) = listeners[index].onClosing(sockets[index], code, "closed")
        fun closed(index: Int, code: Int) = listeners[index].onClosed(sockets[index], code, "closed")
        fun message(index: Int, text: String) = listeners[index].onMessage(sockets[index], text)
    }

    private class TestSocket(private val request: Request) : WebSocket {
        var acceptSend = true
        override fun request(): Request = request
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = acceptSend
        override fun send(bytes: ByteString): Boolean = acceptSend
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() = Unit
    }

    private companion object {
        const val PENDING = """{"type":"pickup.pending","sessionId":"s_one","ts":123,"payload":{}}"""
        fun response(request: Request): Response = Response.Builder().request(request)
            .protocol(HttpProtocol.HTTP_1_1).code(101).message("Switching Protocols").build()
    }
}
