package com.maejung.protocol

import kotlinx.serialization.Serializable

/** REST API 본문(docs/PROTOCOL.md 2절) */

@Serializable
data class VehicleRegistered(val vehicleId: String, val vehicleToken: String)

@Serializable
data class PairingCodeIssued(val code: String, val expiresAt: Long)

@Serializable
data class PairingRequest(val code: String, val riderName: String)

@Serializable
data class PairingResult(val riderId: String, val vehicleId: String, val riderToken: String)

@Serializable
data class ApiError(val code: String, val message: String)

object ApiPaths {
    const val VEHICLES = "/api/vehicles"
    const val PAIRING_CODES = "/api/pairing-codes"
    const val PAIRINGS = "/api/pairings"
    const val HEALTH = "/health"
    const val WEBSOCKET = "/ws"
}
