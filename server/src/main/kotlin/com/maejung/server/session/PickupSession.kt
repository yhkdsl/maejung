package com.maejung.server.session

enum class SessionState { REQUESTED, SHARING }

/**
 * 진행 중인 픽업 세션. 끝나면 메모리에서 지운다.
 * 위치는 중계만 하고 좌표는 저장하지 않는다. 만료 판단에 필요한 마지막 수신 시각만 둔다.
 */
class PickupSession(
    val id: String,
    val vehicleId: String,
    val riderId: String,
    val riderName: String,
    val createdAt: Long,
) {
    var state = SessionState.REQUESTED
    var acceptedAt: Long? = null
    var lastVehicleLocationAt: Long? = null
    var lastRiderLocationAt: Long? = null
}
