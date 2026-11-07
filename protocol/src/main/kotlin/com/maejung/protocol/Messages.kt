package com.maejung.protocol

import kotlinx.serialization.Serializable

/**
 * WebSocket 메시지의 payload(docs/PROTOCOL.md 3절). 메시지 하나가 type 하나에 대응한다.
 * 문자열 값(reason, state, source, code)은 서버가 새 값을 보내도 깨지지 않게 enum 대신 String으로 받고, 아는 값은 상수로 둔다.
 */
sealed interface Message

/** 폰·차 → 서버 */
sealed interface ClientMessage : Message

/** 서버 → 폰·차 */
sealed interface ServerMessage : Message

// ---- 폰 → 서버 ----

@Serializable
data class PickupRequest(val riderName: String) : ClientMessage

// ---- 차 → 서버 ----

@Serializable
data object PickupAccept : ClientMessage

@Serializable
data object PickupReject : ClientMessage

@Serializable
data class EtaUpdate(val seconds: Int, val meters: Int, val source: String) : ClientMessage

@Serializable
data class VehicleLight(val on: Boolean) : ClientMessage

@Serializable
data object SessionBoarded : ClientMessage

// ---- 폰·차 공통 → 서버 ----

@Serializable
data class LocationUpdate(val lat: Double, val lng: Double, val accuracy: Float) : ClientMessage

@Serializable
data class SessionEnd(val reason: String) : ClientMessage

// ---- 서버 → 폰·차 ----

@Serializable
data class SessionState(val state: String, val riderId: String? = null, val riderName: String? = null) : ServerMessage

@Serializable
data class PickupRequested(val riderId: String, val riderName: String) : ServerMessage

@Serializable
data object PickupPending : ServerMessage

@Serializable
data object PickupAccepted : ServerMessage

@Serializable
data class PeerLocation(val lat: Double, val lng: Double, val accuracy: Float) : ServerMessage

@Serializable
data class PeerEta(val seconds: Int, val meters: Int, val source: String) : ServerMessage

@Serializable
data class PeerLight(val on: Boolean) : ServerMessage

@Serializable
data class SessionEnded(val reason: String) : ServerMessage

@Serializable
data class PairingCompleted(val riderId: String, val riderName: String) : ServerMessage

@Serializable
data class ErrorMessage(val code: String, val message: String) : ServerMessage

/** 모르는 type. 새 서버 메시지가 와도 앱이 죽지 않게 예외 대신 이걸로 받는다 */
data class Unknown(val type: String, val raw: String) : ServerMessage, ClientMessage

object SessionStates {
    const val NONE = "none"
    const val REQUESTED = "requested"
    const val SHARING = "sharing"
}

object EndReasons {
    const val BOARDED = "boarded"
    const val MANUAL = "manual"
    const val APP_CLOSED = "app_closed"
    const val REJECTED = "rejected"
    const val REQUEST_TIMEOUT = "request_timeout"
    const val PEER_LOST = "peer_lost"
    const val MAX_DURATION = "max_duration"
}

object EtaSources {
    const val NAVI = "navi"
    const val ESTIMATE = "estimate"
}

object ErrorCodes {
    const val SESSION_ALREADY_ACTIVE = "SESSION_ALREADY_ACTIVE"
    const val VEHICLE_OFFLINE = "VEHICLE_OFFLINE"
    const val SESSION_NOT_FOUND = "SESSION_NOT_FOUND"
    const val INVALID_STATE = "INVALID_STATE"
    const val BAD_MESSAGE = "BAD_MESSAGE"
    const val INVALID_TOKEN = "INVALID_TOKEN"
    const val PAIRING_CODE_NOT_FOUND = "PAIRING_CODE_NOT_FOUND"
    const val INVALID_RIDER_NAME = "INVALID_RIDER_NAME"
}

/** WebSocket close code(PROTOCOL.md 3절). 둘 다 재연결하지 않는다 */
object CloseCodes {
    const val INVALID_TOKEN = 4401
    const val REPLACED = 4409
}
