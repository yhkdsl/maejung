package com.maejung.server.session

import com.maejung.server.ws.Envelope

/** 연결된 클라이언트에게 메시지를 보낸다. 실제 구현은 ConnectionRegistry, 테스트는 가짜 구현 */
interface Outbox {
    /** principal key(vehicleId 또는 riderId)로 보낸다. 연결이 없으면 버린다 */
    fun send(principalKey: String, envelope: Envelope)

    fun isOnline(principalKey: String): Boolean
}
