package com.maejung.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ProtocolTest {
    private val all: List<Pair<Message, String>> = listOf(
        PickupRequest("민지") to "pickup.request",
        PickupAccept to "pickup.accept",
        PickupReject to "pickup.reject",
        EtaUpdate(240, 1200, EtaSources.NAVI) to "eta.update",
        VehicleLight(true) to "vehicle.light",
        SessionBoarded to "session.boarded",
        LocationUpdate(37.4995, 127.0603, 5f) to "location.update",
        SessionEnd(EndReasons.APP_CLOSED) to "session.end",
        SessionState(SessionStates.SHARING, "r_1", "민지") to "session.state",
        SessionState(SessionStates.NONE) to "session.state",
        PickupRequested("r_1", "민지") to "pickup.requested",
        PickupPending to "pickup.pending",
        PickupAccepted to "pickup.accepted",
        PeerLocation(37.4983, 127.0628, 4.5f) to "peer.location",
        PeerEta(60, 300, EtaSources.ESTIMATE) to "peer.eta",
        PeerLight(false) to "peer.light",
        SessionEnded(EndReasons.BOARDED) to "session.ended",
        PairingCompleted("r_1", "민지") to "pairing.completed",
        ErrorMessage(ErrorCodes.VEHICLE_OFFLINE, "차가 연결돼 있지 않습니다") to "error",
    )

    @Test
    fun `모든 메시지가 type과 함께 왕복한다`() {
        all.forEach { (message, type) ->
            val text = Protocol.encode(message, ts = 1_762_412_400_000, sessionId = "s_abc")
            val received = Protocol.decode(text)!!
            assertEquals(message, received.message, type)
            assertEquals("s_abc", received.sessionId)
            assertEquals(1_762_412_400_000, received.ts)
            assertEquals(type, Protocol.typeOf(message))
        }
    }

    @Test
    fun `payload가 없는 메시지는 빈 객체로 나간다`() {
        val text = Protocol.encode(PickupAccept, ts = 1)
        assertEquals("""{"type":"pickup.accept","ts":1,"payload":{}}""", text)
    }

    @Test
    fun `sessionId가 없으면 필드를 빼고, 받을 때는 null이다`() {
        val text = Protocol.encode(PickupRequest("민지"), ts = 1)
        assertEquals("""{"type":"pickup.request","ts":1,"payload":{"riderName":"민지"}}""", text)
        assertNull(Protocol.decode(text)!!.sessionId)
    }

    @Test
    fun `서버가 보내는 형식을 그대로 읽는다`() {
        // server의 Jackson이 실제로 만드는 모양: null 필드 생략, 숫자는 Int·Double
        val state = Protocol.decode("""{"type":"session.state","ts":1762412400000,"payload":{"state":"none"}}""")!!
        assertEquals(SessionState("none"), state.message)
        assertNull(state.sessionId)

        val location = Protocol.decode(
            """{"type":"peer.location","sessionId":"s_1","ts":5,"payload":{"lat":37.4983,"lng":127.0628,"accuracy":5.0}}""",
        )!!
        assertEquals(PeerLocation(37.4983, 127.0628, 5.0f), location.message)

        val eta = Protocol.decode("""{"type":"peer.eta","sessionId":"s_1","ts":5,"payload":{"seconds":240,"meters":1200,"source":"navi"}}""")!!
        assertEquals(PeerEta(240, 1200, "navi"), eta.message)

        val ended = Protocol.decode("""{"type":"session.ended","sessionId":"s_1","ts":5,"payload":{"reason":"rejected"}}""")!!
        assertEquals(SessionEnded(EndReasons.REJECTED), ended.message)
    }

    @Test
    fun `모르는 필드는 무시한다`() {
        val received = Protocol.decode("""{"type":"peer.light","ts":1,"payload":{"on":true,"color":"blue"},"extra":1}""")!!
        assertEquals(PeerLight(true), received.message)
    }

    @Test
    fun `모르는 type은 예외 없이 Unknown이다`() {
        val text = """{"type":"future.thing","ts":1,"payload":{"x":1}}"""
        val unknown = assertIs<Unknown>(Protocol.decode(text)!!.message)
        assertEquals("future.thing", unknown.type)
        assertEquals(text, unknown.raw)
    }

    @Test
    fun `JSON이 아니거나 type이 없거나 payload가 틀리면 null이다`() {
        assertNull(Protocol.decode("{not json"))
        assertNull(Protocol.decode("""{"ts":1,"payload":{}}"""))
        assertNull(Protocol.decode("""{"type":"peer.light","ts":1,"payload":{"on":"maybe"}}"""))
        assertNull(Protocol.decode("""{"type":"pickup.requested","ts":1,"payload":{}}"""))
    }

    @Test
    fun `payload가 빠진 data object 메시지도 읽는다`() {
        assertEquals(PickupPending, Protocol.decode("""{"type":"pickup.pending","sessionId":"s_1","ts":1}""")!!.message)
    }

    @Test
    fun `REST 본문을 서버 응답 그대로 읽는다`() {
        val registered = Protocol.json.decodeFromString<VehicleRegistered>("""{"vehicleId":"v_1","vehicleToken":"t"}""")
        assertEquals(VehicleRegistered("v_1", "t"), registered)
        val issued = Protocol.json.decodeFromString<PairingCodeIssued>("""{"code":"048213","expiresAt":1762412400000}""")
        assertEquals("048213", issued.code)
        assertEquals(
            """{"code":"048213","riderName":"민지"}""",
            Protocol.json.encodeToString(PairingRequest.serializer(), PairingRequest("048213", "민지")),
        )
        val error = Protocol.json.decodeFromString<ApiError>("""{"code":"INVALID_TOKEN","message":"인증 토큰이 없습니다"}""")
        assertEquals(ErrorCodes.INVALID_TOKEN, error.code)
    }
}
