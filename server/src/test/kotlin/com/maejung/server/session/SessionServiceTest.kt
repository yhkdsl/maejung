package com.maejung.server.session

import com.maejung.server.MutableClock
import com.maejung.server.token.Principal
import com.maejung.server.ws.Envelope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class SessionServiceTest {
    private class FakeOutbox : Outbox {
        val online = mutableSetOf<String>()
        val sent = mutableListOf<Pair<String, Envelope>>()

        override fun send(principalKey: String, envelope: Envelope) {
            sent += principalKey to envelope
        }

        override fun isOnline(principalKey: String) = principalKey in online

        fun to(key: String) = sent.filter { it.first == key }.map { it.second }
        fun last(key: String) = to(key).last()
        fun types(key: String) = to(key).map { it.type }
    }

    private val clock = MutableClock()
    private val outbox = FakeOutbox()
    private val service = SessionService(outbox, clock)
    private val car = Principal.Vehicle("v_1")
    private val minji = Principal.Rider("v_1", "r_1")
    private val sibling = Principal.Rider("v_1", "r_2")

    @BeforeEach
    fun carOnline() {
        outbox.online += car.key
    }

    private fun msg(type: String, payload: Map<String, Any?> = emptyMap()) = Envelope(type, payload = payload)
    private fun request(rider: Principal.Rider = minji) = service.handle(rider, msg("pickup.request", mapOf("riderName" to "민지")))
    private fun location() = msg("location.update", mapOf("lat" to 37.4995, "lng" to 127.0603, "accuracy" to 5.0))

    private fun sharing() {
        request()
        service.handle(car, msg("pickup.accept"))
    }

    private fun endedReason(key: String) = outbox.to(key).last { it.type == "session.ended" }.payload["reason"]

    @Test
    fun `요청하면 차에 pickup_requested, 폰에 pickup_pending이 간다`() {
        request()
        val requested = outbox.last(car.key)
        assertEquals("pickup.requested", requested.type)
        assertEquals("민지", requested.payload["riderName"])
        assertEquals("pickup.pending", outbox.last(minji.key).type)
        assertEquals(requested.sessionId, outbox.last(minji.key).sessionId)
    }

    @Test
    fun `차가 오프라인이면 VEHICLE_OFFLINE이고 세션을 만들지 않는다`() {
        outbox.online -= car.key
        request()
        assertEquals("VEHICLE_OFFLINE", outbox.last(minji.key).payload["code"])
        service.onConnected(car)
        assertEquals("none", outbox.last(car.key).payload["state"])
    }

    @Test
    fun `진행 중 세션이 있으면 SESSION_ALREADY_ACTIVE`() {
        request()
        request()
        assertEquals("SESSION_ALREADY_ACTIVE", outbox.last(minji.key).payload["code"])
        request(sibling)
        assertEquals("SESSION_ALREADY_ACTIVE", outbox.last(sibling.key).payload["code"])
    }

    @Test
    fun `이름이 비어 있으면 BAD_MESSAGE`() {
        service.handle(minji, msg("pickup.request", mapOf("riderName" to " ")))
        assertEquals("BAD_MESSAGE", outbox.last(minji.key).payload["code"])
    }

    @Test
    fun `승인하면 폰에 pickup_accepted가 가고 위치가 서로 중계된다`() {
        sharing()
        assertEquals("pickup.accepted", outbox.last(minji.key).type)
        service.handle(minji, location())
        assertEquals("peer.location", outbox.last(car.key).type)
        assertEquals(37.4995, outbox.last(car.key).payload["lat"])
        service.handle(car, location())
        assertEquals("peer.location", outbox.last(minji.key).type)
    }

    @Test
    fun `SHARING이 아니면 위치는 조용히 버린다`() {
        request()
        val before = outbox.sent.size
        service.handle(minji, location())
        service.handle(car, location())
        assertEquals(before, outbox.sent.size)
    }

    @Test
    fun `ETA와 실내등은 폰에 그대로 전달된다`() {
        sharing()
        service.handle(car, msg("eta.update", mapOf("seconds" to 240, "meters" to 1200, "source" to "estimate")))
        assertEquals("peer.eta", outbox.last(minji.key).type)
        assertEquals(240, outbox.last(minji.key).payload["seconds"])
        service.handle(car, msg("vehicle.light", mapOf("on" to true)))
        assertEquals(mapOf("on" to true), outbox.last(minji.key).payload)
    }

    @Test
    fun `탑승하면 양쪽에 boarded로 끝난다`() {
        sharing()
        service.handle(car, msg("session.boarded"))
        assertEquals("boarded", endedReason(car.key))
        assertEquals("boarded", endedReason(minji.key))
        service.onConnected(minji)
        assertEquals("none", outbox.last(minji.key).payload["state"])
    }

    @Test
    fun `거절하면 양쪽에 rejected 하나만 간다`() {
        request()
        service.handle(car, msg("pickup.reject"))
        assertEquals("rejected", endedReason(minji.key))
        assertEquals("rejected", endedReason(car.key))
        assertTrue(outbox.types(minji.key).none { it == "pickup.rejected" })
    }

    @Test
    fun `폰이나 차의 session_end는 manual로 끝난다`() {
        request()
        service.handle(minji, msg("session.end", mapOf("reason" to "app_closed")))
        assertEquals("manual", endedReason(car.key))

        sharing()
        service.handle(car, msg("session.end", mapOf("reason" to "manual")))
        assertEquals("manual", endedReason(minji.key))
    }

    @Test
    fun `상태에 맞지 않는 메시지는 INVALID_STATE, 세션이 없으면 SESSION_NOT_FOUND`() {
        request()
        service.handle(car, msg("session.boarded"))
        assertEquals("INVALID_STATE", outbox.last(car.key).payload["code"])
        service.handle(car, msg("pickup.accept"))
        service.handle(car, msg("pickup.accept"))
        assertEquals("INVALID_STATE", outbox.last(car.key).payload["code"])

        service.handle(car, msg("session.end"))
        service.handle(car, msg("pickup.accept"))
        assertEquals("SESSION_NOT_FOUND", outbox.last(car.key).payload["code"])
    }

    @Test
    fun `모르는 type은 BAD_MESSAGE`() {
        service.handle(car, msg("hello"))
        assertEquals("BAD_MESSAGE", outbox.last(car.key).payload["code"])
    }

    @Test
    fun `요청 후 5분 동안 답이 없으면 request_timeout`() {
        request()
        clock.advance(Duration.ofMinutes(4).plusSeconds(59))
        service.sweep()
        service.onConnected(car)
        assertEquals("requested", outbox.last(car.key).payload["state"])
        clock.advance(Duration.ofSeconds(1))
        service.sweep()
        assertEquals("request_timeout", endedReason(minji.key))
    }

    @Test
    fun `위치를 한 번도 안 보내면 승인 시각부터 2분 뒤 peer_lost`() {
        request()
        clock.advance(Duration.ofMinutes(3))
        service.handle(car, msg("pickup.accept"))
        clock.advance(Duration.ofMinutes(2))
        service.sweep()
        service.onConnected(minji)
        assertEquals("sharing", outbox.last(minji.key).payload["state"])
        clock.advance(Duration.ofSeconds(1))
        service.sweep()
        assertEquals("peer_lost", endedReason(car.key))
    }

    @Test
    fun `한쪽만 계속 보내도 다른 쪽이 2분 넘게 끊기면 peer_lost`() {
        sharing()
        repeat(13) {
            clock.advance(Duration.ofSeconds(10))
            service.handle(car, location())
            service.sweep()
        }
        assertEquals("peer_lost", endedReason(car.key))
    }

    @Test
    fun `양쪽이 계속 보내면 60분에 max_duration`() {
        sharing()
        repeat(359) {
            clock.advance(Duration.ofSeconds(10))
            service.handle(car, location())
            service.handle(minji, location())
            service.sweep()
        }
        service.onConnected(car)
        assertEquals("sharing", outbox.last(car.key).payload["state"])
        clock.advance(Duration.ofSeconds(10))
        service.sweep()
        assertEquals("max_duration", endedReason(minji.key))
    }

    @Test
    fun `연결 직후 session_state로 진행 중 세션을 알려 준다`() {
        service.onConnected(minji)
        assertEquals("none", outbox.last(minji.key).payload["state"])
        request()
        service.onConnected(car)
        val state = outbox.last(car.key)
        assertEquals("session.state", state.type)
        assertEquals("requested", state.payload["state"])
        assertEquals("민지", state.payload["riderName"])
        assertEquals(outbox.to(car.key).first { it.type == "pickup.requested" }.sessionId, state.sessionId)
    }
}
