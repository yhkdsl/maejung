package com.maejung.server.session

import com.maejung.server.token.Principal
import com.maejung.server.token.TokenService
import com.maejung.server.ws.Envelope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/**
 * 픽업 세션의 상태 전이와 메시지 중계(PROTOCOL.md 3·4절).
 * 1차는 서버 한 대·소규모라 전체를 하나의 락으로 묶는다.
 */
@Service
class SessionService(
    private val outbox: Outbox,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val sessions = HashMap<String, PickupSession>()
    private val sessionByVehicle = HashMap<String, String>()
    private val sessionByRider = HashMap<String, String>()

    /** WebSocket 연결 직후 진행 중 세션을 알려 클라이언트 상태를 맞춘다(PROTOCOL.md 5절) */
    @Synchronized
    fun onConnected(principal: Principal) {
        val session = findFor(principal)
        val payload = when (session?.state) {
            null -> mapOf("state" to "none")
            else -> mapOf(
                "state" to session.state.name.lowercase(),
                "riderId" to session.riderId,
                "riderName" to session.riderName,
            )
        }
        send(principal.key, "session.state", session?.id, payload)
    }

    @Synchronized
    fun handle(principal: Principal, message: Envelope) {
        log.info("recv type={} sessionId={} from={}", message.type, findFor(principal)?.id, kind(principal))
        when (principal) {
            is Principal.Rider -> handleRider(principal, message)
            is Principal.Vehicle -> handleVehicle(principal, message)
        }
    }

    /** 만료 검사. SessionSweeper가 5초마다 부른다 */
    @Synchronized
    fun sweep() {
        val now = clock.millis()
        sessions.values.toList().forEach { session ->
            val reason = when {
                now - session.createdAt >= MAX_DURATION.toMillis() -> "max_duration"
                session.state == SessionState.REQUESTED && now - session.createdAt >= REQUEST_TIMEOUT.toMillis() ->
                    "request_timeout"
                session.state == SessionState.SHARING && isPeerLost(session, now) -> "peer_lost"
                else -> null
            }
            reason?.let { end(session, it) }
        }
    }

    private fun handleRider(rider: Principal.Rider, message: Envelope) {
        when (message.type) {
            "pickup.request" -> request(rider, message)
            "location.update" -> sessionByRider[rider.riderId]?.let(sessions::get)
                ?.takeIf { it.state == SessionState.SHARING }
                ?.let { session ->
                    session.lastRiderLocationAt = clock.millis()
                    send(session.vehicleId, "peer.location", session.id, location(message))
                }
            "session.end" -> sessionByRider[rider.riderId]?.let(sessions::get)?.let { end(it, "manual") }
                ?: error(rider, "SESSION_NOT_FOUND", "진행 중인 세션이 없습니다")
            else -> error(rider, "BAD_MESSAGE", "모르는 메시지입니다: ${message.type}")
        }
    }

    private fun handleVehicle(vehicle: Principal.Vehicle, message: Envelope) {
        val session = sessionByVehicle[vehicle.vehicleId]?.let(sessions::get)
        when (message.type) {
            "location.update", "eta.update", "vehicle.light" -> {
                // SHARING이 아닐 때 오는 위치·ETA·실내등은 조용히 버린다
                if (session?.state != SessionState.SHARING) return
                when (message.type) {
                    "location.update" -> {
                        session.lastVehicleLocationAt = clock.millis()
                        send(session.riderId, "peer.location", session.id, location(message))
                    }
                    "eta.update" -> send(session.riderId, "peer.eta", session.id, message.payload)
                    else -> send(session.riderId, "peer.light", session.id, mapOf("on" to (message.payload["on"] == true)))
                }
            }
            "pickup.accept", "pickup.reject", "session.boarded", "session.end" -> {
                if (session == null) return error(vehicle, "SESSION_NOT_FOUND", "진행 중인 세션이 없습니다")
                when (message.type) {
                    "pickup.accept" -> requireState(vehicle, session, SessionState.REQUESTED) {
                        session.state = SessionState.SHARING
                        session.acceptedAt = clock.millis()
                        send(session.riderId, "pickup.accepted", session.id)
                    }
                    "pickup.reject" -> requireState(vehicle, session, SessionState.REQUESTED) { end(session, "rejected") }
                    "session.boarded" -> requireState(vehicle, session, SessionState.SHARING) { end(session, "boarded") }
                    else -> end(session, "manual")
                }
            }
            else -> error(vehicle, "BAD_MESSAGE", "모르는 메시지입니다: ${message.type}")
        }
    }

    private fun request(rider: Principal.Rider, message: Envelope) {
        val riderName = (message.payload["riderName"] as? String)?.trim().orEmpty()
        if (riderName.length !in 1..20) return error(rider, "BAD_MESSAGE", "riderName은 1~20자여야 합니다")
        if (!outbox.isOnline(rider.vehicleId)) return error(rider, "VEHICLE_OFFLINE", "차가 연결돼 있지 않습니다")
        if (sessionByRider.containsKey(rider.riderId) || sessionByVehicle.containsKey(rider.vehicleId)) {
            return error(rider, "SESSION_ALREADY_ACTIVE", "이미 진행 중인 세션이 있습니다")
        }
        val session = PickupSession(TokenService.newId("s"), rider.vehicleId, rider.riderId, riderName, clock.millis())
        sessions[session.id] = session
        sessionByVehicle[session.vehicleId] = session.id
        sessionByRider[session.riderId] = session.id
        send(session.vehicleId, "pickup.requested", session.id, mapOf("riderId" to session.riderId, "riderName" to riderName))
        send(session.riderId, "pickup.pending", session.id)
    }

    /** 세션과 마지막 수신 기록을 지우고 양쪽에 알린다. 거절도 session.ended(rejected) 하나로 알린다 */
    private fun end(session: PickupSession, reason: String) {
        sessions.remove(session.id)
        sessionByVehicle.remove(session.vehicleId)
        sessionByRider.remove(session.riderId)
        log.info("ended sessionId={} reason={}", session.id, reason)
        val payload = mapOf("reason" to reason)
        send(session.vehicleId, "session.ended", session.id, payload)
        send(session.riderId, "session.ended", session.id, payload)
    }

    /** 어느 한쪽의 마지막 위치가 2분 넘게 안 왔다. 한 번도 안 보낸 쪽은 승인 시각부터 센다 */
    private fun isPeerLost(session: PickupSession, now: Long): Boolean {
        val since = session.acceptedAt ?: return false
        val limit = PEER_LOST.toMillis()
        return now - (session.lastVehicleLocationAt ?: since) > limit || now - (session.lastRiderLocationAt ?: since) > limit
    }

    private inline fun requireState(principal: Principal, session: PickupSession, expected: SessionState, action: () -> Unit) {
        if (session.state == expected) action() else error(principal, "INVALID_STATE", "지금 상태(${session.state})에서는 할 수 없습니다")
    }

    private fun findFor(principal: Principal): PickupSession? = when (principal) {
        is Principal.Vehicle -> sessionByVehicle[principal.vehicleId]
        is Principal.Rider -> sessionByRider[principal.riderId]
    }?.let(sessions::get)

    private fun location(message: Envelope) = mapOf(
        "lat" to message.payload["lat"],
        "lng" to message.payload["lng"],
        "accuracy" to message.payload["accuracy"],
    )

    private fun error(principal: Principal, code: String, message: String) =
        send(principal.key, "error", null, mapOf("code" to code, "message" to message))

    private fun send(to: String, type: String, sessionId: String?, payload: Map<String, Any?> = emptyMap()) =
        outbox.send(to, Envelope(type, sessionId, clock.millis(), payload))

    private fun kind(principal: Principal) = if (principal is Principal.Vehicle) "v" else "r"

    companion object {
        val REQUEST_TIMEOUT: Duration = Duration.ofMinutes(5)
        val PEER_LOST: Duration = Duration.ofMinutes(2)
        val MAX_DURATION: Duration = Duration.ofMinutes(60)
    }
}
