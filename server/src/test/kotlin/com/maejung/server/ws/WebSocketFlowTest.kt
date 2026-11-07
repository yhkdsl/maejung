package com.maejung.server.ws

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** 실제 서버를 띄우고 차·폰 WebSocket 두 개로 PROTOCOL.md 흐름을 확인한다(SERVER.md 6절 ①~⑤) */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketFlowTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var rest: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private inner class Client(token: String) : TextWebSocketHandler() {
        val inbox = LinkedBlockingQueue<Envelope>()
        val closed = CompletableFuture<CloseStatus>()
        val session: WebSocketSession =
            StandardWebSocketClient().execute(this, "ws://localhost:$port/ws?token=$token").get(5, TimeUnit.SECONDS)

        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            inbox += objectMapper.readValue<Envelope>(message.payload)
        }

        override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
            closed.complete(status)
        }

        fun next(): Envelope = inbox.poll(5, TimeUnit.SECONDS).also { assertNotNull(it, "메시지가 오지 않았습니다") }!!

        fun expect(type: String): Envelope = next().also { assertEquals(type, it.type) }

        fun send(type: String, payload: Map<String, Any?> = emptyMap()) =
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(Envelope(type, payload = payload))))
    }

    private fun registerVehicle(): String =
        rest.postForEntity("/api/vehicles", null, Map::class.java).body!!["vehicleToken"] as String

    private fun pairRider(vehicleToken: String, name: String = "민지"): String {
        val headers = HttpHeaders().apply { setBearerAuth(vehicleToken) }
        val code = rest.exchange("/api/pairing-codes", HttpMethod.POST, HttpEntity<Unit>(headers), Map::class.java)
            .body!!["code"] as String
        return rest.postForEntity("/api/pairings", mapOf("code" to code, "riderName" to name), Map::class.java)
            .body!!["riderToken"] as String
    }

    @Test
    fun `① 틀린 토큰은 4401로 닫힌다`() {
        val client = Client("not-a-token")
        assertEquals(4401, client.closed.get(5, TimeUnit.SECONDS).code)
    }

    @Test
    fun `REST 오류 형식`() {
        val noToken = rest.postForEntity("/api/pairing-codes", null, Map::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, noToken.statusCode)
        assertEquals("INVALID_TOKEN", noToken.body!!["code"])

        val badCode = rest.postForEntity("/api/pairings", mapOf("code" to "999999x", "riderName" to "민지"), Map::class.java)
        assertEquals(HttpStatus.NOT_FOUND, badCode.statusCode)
        assertEquals("PAIRING_CODE_NOT_FOUND", badCode.body!!["code"])

        val badName = rest.postForEntity("/api/pairings", mapOf("code" to "123456", "riderName" to ""), Map::class.java)
        assertEquals("INVALID_RIDER_NAME", badName.body!!["code"])
    }

    @Test
    fun `②③ 연결 직후 none, 요청부터 탑승까지`() {
        val vehicleToken = registerVehicle()
        val car = Client(vehicleToken)
        assertEquals("none", car.expect("session.state").payload["state"])

        val riderToken = pairRider(vehicleToken)
        assertEquals("민지", car.expect("pairing.completed").payload["riderName"])

        val phone = Client(riderToken)
        assertEquals("none", phone.expect("session.state").payload["state"])

        phone.send("pickup.request", mapOf("riderName" to "민지"))
        val requested = car.expect("pickup.requested")
        assertEquals(requested.sessionId, phone.expect("pickup.pending").sessionId)

        car.send("pickup.accept")
        phone.expect("pickup.accepted")

        phone.send("location.update", mapOf("lat" to 37.4983, "lng" to 127.0628, "accuracy" to 5.0))
        assertEquals(127.0628, car.expect("peer.location").payload["lng"])
        car.send("location.update", mapOf("lat" to 37.4995, "lng" to 127.0603, "accuracy" to 5.0))
        assertEquals(37.4995, phone.expect("peer.location").payload["lat"])
        car.send("vehicle.light", mapOf("on" to true))
        assertEquals(true, phone.expect("peer.light").payload["on"])

        car.send("session.boarded")
        assertEquals("boarded", car.expect("session.ended").payload["reason"])
        assertEquals("boarded", phone.expect("session.ended").payload["reason"])
    }

    @Test
    fun `④ 같은 토큰으로 다시 붙으면 이전 연결은 4409로 닫힌다`() {
        val vehicleToken = registerVehicle()
        val first = Client(vehicleToken)
        first.expect("session.state")
        val second = Client(vehicleToken)
        second.expect("session.state")
        assertEquals(4409, first.closed.get(5, TimeUnit.SECONDS).code)
    }

    @Test
    fun `⑤ SHARING 중 폰이 다시 붙으면 session_state sharing을 받는다`() {
        val vehicleToken = registerVehicle()
        val car = Client(vehicleToken)
        car.expect("session.state")
        val riderToken = pairRider(vehicleToken)
        car.expect("pairing.completed")
        val phone = Client(riderToken)
        phone.expect("session.state")
        phone.send("pickup.request", mapOf("riderName" to "민지"))
        car.expect("pickup.requested")
        phone.expect("pickup.pending")
        car.send("pickup.accept")
        phone.expect("pickup.accepted")

        phone.session.close()
        val again = Client(riderToken)
        val state = again.expect("session.state")
        assertEquals("sharing", state.payload["state"])
        assertEquals("민지", state.payload["riderName"])
    }

    @Test
    fun `차가 연결돼 있지 않으면 VEHICLE_OFFLINE`() {
        val riderToken = pairRider(registerVehicle())
        val phone = Client(riderToken)
        phone.expect("session.state")
        phone.send("pickup.request", mapOf("riderName" to "민지"))
        assertEquals("VEHICLE_OFFLINE", phone.expect("error").payload["code"])
    }

    @Test
    fun `JSON이 아니면 BAD_MESSAGE`() {
        val car = Client(registerVehicle())
        car.expect("session.state")
        car.session.sendMessage(TextMessage("{not json"))
        assertEquals("BAD_MESSAGE", car.expect("error").payload["code"])
    }
}
