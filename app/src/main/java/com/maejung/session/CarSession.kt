package com.maejung.session

import com.maejung.protocol.ClientMessage
import com.maejung.protocol.Received
import com.maejung.vehicle.CabinLightInfo
import com.maejung.vehicle.CabinSwitch
import com.maejung.vehicle.Gear
import com.maejung.vehicle.PassengerDoor

/** 차 앱 세션 단계(docs/CAR_APP.md 3.1). 서버는 REQUESTED·SHARING·ENDED만 알고 Nearby는 차 앱 안에서만 쓴다 */
sealed interface Phase {
    data object Idle : Phase

    data class Requested(val sessionId: String?, val riderName: String) : Phase

    data class Sharing(val sessionId: String?, val riderName: String) : Phase

    data class Nearby(val sessionId: String?, val riderName: String, val openedDoors: Set<PassengerDoor> = emptySet()) : Phase

    data class Ended(val reason: String) : Phase
}

data class Peer(val lat: Double, val lng: Double)

/**
 * 단계와, 단계가 바뀌어도 늘 들고 있는 컨텍스트.
 * `lightBefore`는 Nearby에서 실내등을 켜기 전 값이다. null이면 켠 적이 없다.
 * `navDecided`는 이번 세션에서 길안내 여부를 이미 판단했는지(첫 거리 계산 때 한 번만 판단한다).
 */
data class CarSession(
    val phase: Phase = Phase.Idle,
    val gear: Gear? = null,
    val distanceM: Double? = null,
    val lightInfo: CabinLightInfo? = null,
    val lightBefore: CabinSwitch? = null,
    val peer: Peer? = null,
    val navStarted: Boolean = false,
    val navDecided: Boolean = false,
)

sealed interface CarEvent {
    data class Server(val received: Received) : CarEvent

    data class GearChanged(val gear: Gear) : CarEvent

    /** 승객 문만. open=true가 열림(SDK 값 1) */
    data class Door(val door: PassengerDoor, val open: Boolean) : CarEvent

    data class CabinLight(val info: CabinLightInfo) : CarEvent

    /** 차·탑승자 위치로 Manager가 계산한 거리. 한쪽 위치가 없으면 null */
    data class Distance(val meters: Double?) : CarEvent

    data object UserAccept : CarEvent

    data object UserReject : CarEvent

    data object UserEnd : CarEvent

    /** Ended 진입 3초 뒤 */
    data object EndedTimeout : CarEvent
}

sealed interface CarEffect {
    data class Send(val message: ClientMessage) : CarEffect

    data class SetCabinLight(val value: CabinSwitch) : CarEffect

    /** Prefs에 실내등 원래 값을 남긴다. 앱이 죽었다 살아나도 복구할 수 있게 한다 */
    data class SaveLightBefore(val value: CabinSwitch?) : CarEffect

    data class Speak(val text: String) : CarEffect

    data object Listen : CarEffect

    data object StartLocationSharing : CarEffect

    data object StopLocationSharing : CarEffect

    data class ShowRequestAlert(val riderName: String) : CarEffect

    data object DismissRequestAlert : CarEffect

    /** 버튼 없는 헤드업 알림 */
    data class Notify(val text: String) : CarEffect

    data class StartNavigation(val lat: Double, val lng: Double, val riderName: String) : CarEffect

    data object StopNavigation : CarEffect
}
