package com.maejung.phone.net

import com.maejung.protocol.PairingResult
import com.maejung.protocol.Protocol
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: ApiClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient()
        api = ApiClient(server.url("/").toString(), client)
    }

    @AfterTest
    fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    @Test
    fun `pair posts shared JSON preserving leading zero and internal name spaces`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(SUCCESS))

        assertEquals(PairingResult("r_one", "v_one", "signed.token"), api.pair("012345", "  김  민지\n"))

        val request = assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/api/pairings", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        val body = Protocol.json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("012345", body["code"]?.jsonPrimitive?.content)
        assertEquals("김  민지", body["riderName"]?.jsonPrimitive?.content)
        assertEquals(setOf("code", "riderName"), body.keys)
        assertEquals(null, request.getHeader("Authorization"))
    }

    @Test
    fun `structured REST errors retain HTTP status code and message`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"PAIRING_CODE_NOT_FOUND","message":"expired"}"""))
        val error = assertFailsWith<ApiException> { api.pair("012345", "민지") }
        assertEquals(404, error.status)
        assertEquals("PAIRING_CODE_NOT_FOUND", error.code)
        assertEquals("expired", error.message)
    }

    @Test
    fun `non JSON server errors use safe HTTP fallback`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("<html>signed.token</html>"))
        val error = assertFailsWith<ApiException> { api.pair("012345", "민지") }
        assertEquals(503, error.status)
        assertEquals(null, error.code)
        assertEquals("HTTP 503", error.message)
    }

    @Test
    fun `malformed incomplete and blank successful responses are IO failures`() = runBlocking {
        for (body in listOf("not JSON", "{}", """{"riderId":"r_one","vehicleId":"v_one","riderToken":" "}""")) {
            server.enqueue(MockResponse().setResponseCode(201).setBody(body))
            assertFailsWith<IOException>(body) { api.pair("012345", "민지") }
        }
    }

    @Test
    fun `name validation precedes code validation and uses UTF16 length`() = runBlocking {
        for (name in listOf("", " \n ", "가".repeat(21), "😀".repeat(11))) {
            val error = assertFailsWith<ApiException> { api.pair("bad", name) }
            assertEquals(400, error.status)
            assertEquals("INVALID_RIDER_NAME", error.code)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `codes remain exactly six ASCII digits without trimming`() = runBlocking {
        for (code in listOf("", "12345", "1234567", "１２３４５６", "١٢٣٤٥٦", " 123456", "123456\n")) {
            val error = assertFailsWith<ApiException> { api.pair(code, "민지") }
            assertEquals("PAIRING_CODE_NOT_FOUND", error.code)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `twenty UTF16 units is a valid name and unknown response fields are ignored`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(SUCCESS.dropLast(1) + ",\"future\":true}"))
        assertEquals("r_one", api.pair("000001", "😀".repeat(10)).riderId)
    }

    @Test
    fun `cancelling pairing cancels the in flight OkHttp call promptly`() = runBlocking {
        val cancelled = CountDownLatch(1)
        val cancellableClient = client.newBuilder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.countDown() }
        }).build()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(Dispatchers.Default) {
            ApiClient(server.url("/").toString(), cancellableClient).pair("012345", "민지")
        }
        try {
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS), "pair must issue an actual HTTP call")
            withTimeout(3_000) { pending.cancelAndJoin() }
            assertTrue(cancelled.await(2, TimeUnit.SECONDS), "coroutine cancellation must cancel the HTTP call")
        } finally {
            pending.cancelAndJoin()
        }
    }

    private companion object {
        const val SUCCESS = """{"riderId":"r_one","vehicleId":"v_one","riderToken":"signed.token"}"""
    }
}
