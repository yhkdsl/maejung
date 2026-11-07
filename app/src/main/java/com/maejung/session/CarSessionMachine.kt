package com.maejung.session

import com.maejung.protocol.EndReasons
import com.maejung.protocol.PeerLocation
import com.maejung.protocol.PickupAccept
import com.maejung.protocol.PickupReject
import com.maejung.protocol.PickupRequested
import com.maejung.protocol.SessionBoarded
import com.maejung.protocol.SessionEnd
import com.maejung.protocol.SessionEnded
import com.maejung.protocol.SessionState
import com.maejung.protocol.SessionStates
import com.maejung.protocol.VehicleLight
import com.maejung.session.CarEffect.DismissRequestAlert
import com.maejung.session.CarEffect.Listen
import com.maejung.session.CarEffect.Notify
import com.maejung.session.CarEffect.SaveLightBefore
import com.maejung.session.CarEffect.Send
import com.maejung.session.CarEffect.SetCabinLight
import com.maejung.session.CarEffect.ShowRequestAlert
import com.maejung.session.CarEffect.Speak
import com.maejung.session.CarEffect.StartLocationSharing
import com.maejung.session.CarEffect.StartNavigation
import com.maejung.session.CarEffect.StopLocationSharing
import com.maejung.session.CarEffect.StopNavigation
import com.maejung.vehicle.CabinSwitch
import com.maejung.vehicle.Gear

/**
 * 차 앱 상태 머신(docs/CAR_APP.md 3.4 전이 표). Android에 의존하지 않는 순수 함수다.
 * 표에 없는 조합은 상태를 바꾸지 않고 효과도 내지 않는다.
 */
object CarSessionMachine {
    const val NAVIGATION_MIN_METERS = 500.0
    const val NEARBY_ENTER_METERS = 50.0
    const val NEARBY_EXIT_METERS = 70.0
    const val BOARDING_MAX_METERS = 30.0

    fun reduce(session: CarSession, event: CarEvent): Pair<CarSession, List<CarEffect>> {
        // 컨텍스트(기어·거리·실내등·탑승자 좌표)는 단계와 관계없이 먼저 갱신한다
        val updated = updateContext(session, event)
        return when (val phase = updated.phase) {
            Phase.Idle -> idle(updated, event)
            is Phase.Requested -> requested(updated, phase, event)
            is Phase.Sharing -> sharing(updated, phase, event)
            is Phase.Nearby -> nearby(updated, phase, event)
            is Phase.Ended -> ended(updated, event)
        }
    }

    private fun updateContext(session: CarSession, event: CarEvent): CarSession = when (event) {
        is CarEvent.GearChanged -> session.copy(gear = event.gear)
        is CarEvent.Distance -> session.copy(distanceM = event.meters)
        is CarEvent.CabinLight -> session.copy(lightInfo = event.info)
        is CarEvent.Server -> (event.received.message as? PeerLocation)
            ?.let { session.copy(peer = Peer(it.lat, it.lng)) } ?: session
        else -> session
    }

    private fun idle(session: CarSession, event: CarEvent): Pair<CarSession, List<CarEffect>> {
        val server = event as? CarEvent.Server ?: return session to emptyList()
        return when (val message = server.received.message) {
            is PickupRequested -> request(session, server.received.sessionId, message.riderName)
            is SessionState -> when (message.state) {
                SessionStates.REQUESTED ->
                    newSession(session, Phase.Requested(server.received.sessionId, message.riderName.orEmpty())) to emptyList()
                SessionStates.SHARING ->
                    newSession(session, Phase.Sharing(server.received.sessionId, message.riderName.orEmpty())) to
                        listOf(StartLocationSharing)
                else -> session to emptyList()
            }
            else -> session to emptyList()
        }
    }

    private fun requested(session: CarSession, phase: Phase.Requested, event: CarEvent): Pair<CarSession, List<CarEffect>> =
        when (event) {
            CarEvent.UserAccept -> session.copy(phase = Phase.Sharing(phase.sessionId, phase.riderName)) to
                listOf(DismissRequestAlert, Send(PickupAccept), StartLocationSharing)
            CarEvent.UserReject -> end(session, EndReasons.REJECTED, listOf(Send(PickupReject)))
            CarEvent.UserEnd -> end(session, EndReasons.MANUAL, listOf(Send(SessionEnd(EndReasons.MANUAL))))
            is CarEvent.Server -> when (val message = event.received.message) {
                is SessionEnded -> end(session, message.reason)
                is SessionState -> when (message.state) {
                    SessionStates.NONE -> end(session, EndReasons.PEER_LOST)
                    // 재연결 사이에 수락이 반영된 경우. 요청 알림도 닫는다
                    SessionStates.SHARING -> session.copy(phase = Phase.Sharing(phase.sessionId, phase.riderName)) to
                        listOf(DismissRequestAlert, StartLocationSharing)
                    else -> session to emptyList()
                }
                else -> session to emptyList()
            }
            else -> session to emptyList()
        }

    private fun sharing(session: CarSession, phase: Phase.Sharing, event: CarEvent): Pair<CarSession, List<CarEffect>> {
        when (event) {
            CarEvent.UserEnd -> return end(session, EndReasons.MANUAL, listOf(Send(SessionEnd(EndReasons.MANUAL))))
            is CarEvent.Server -> when (val message = event.received.message) {
                is SessionEnded -> return end(session, message.reason)
                is SessionState -> if (message.state == SessionStates.NONE) return end(session, EndReasons.PEER_LOST)
                else -> Unit
            }
            else -> Unit
        }
        if (event !is CarEvent.Distance && event !is CarEvent.GearChanged) return session to emptyList()

        var next = session
        val effects = mutableListOf<CarEffect>()
        val distance = session.distanceM
        // 길안내는 이 세션에서 거리가 처음 계산됐을 때 한 번만 판단한다
        if (event is CarEvent.Distance && distance != null && !session.navDecided) {
            next = next.copy(navDecided = true)
            val peer = session.peer
            if (distance >= NAVIGATION_MIN_METERS && peer != null) {
                next = next.copy(navStarted = true)
                effects += StartNavigation(peer.lat, peer.lng, phase.riderName)
                effects += Speak("${phase.riderName.toObject()} 가는 길을 안내할게요")
            }
        }
        if (distance != null && distance <= NEARBY_ENTER_METERS && session.gear == Gear.PARK) {
            val (nearby, nearbyEffects) = enterNearby(next, phase)
            return nearby to effects + nearbyEffects
        }
        return next to effects
    }

    private fun enterNearby(session: CarSession, phase: Phase.Sharing): Pair<CarSession, List<CarEffect>> {
        var next = session.copy(phase = Phase.Nearby(phase.sessionId, phase.riderName))
        val effects = mutableListOf<CarEffect>()
        if (session.lightInfo?.supportsOn == true) {
            // 앱 재시작으로 원래 값이 이미 복원돼 있으면 덮어쓰지 않는다
            if (session.lightBefore == null) {
                val before = session.lightInfo.current ?: CabinSwitch.OFF
                next = next.copy(lightBefore = before)
                effects += SaveLightBefore(before)
            }
            effects += SetCabinLight(CabinSwitch.ON)
            effects += Send(VehicleLight(on = true))
        }
        val text = "${phase.riderName.toSubject()} 가까이 왔어요"
        effects += Notify(text)
        effects += Speak(text)
        return next to effects
    }

    private fun nearby(session: CarSession, phase: Phase.Nearby, event: CarEvent): Pair<CarSession, List<CarEffect>> {
        when (event) {
            CarEvent.UserEnd -> return end(session, EndReasons.MANUAL, listOf(Send(SessionEnd(EndReasons.MANUAL))))
            is CarEvent.Server -> when (val message = event.received.message) {
                is SessionEnded -> return end(session, message.reason)
                is SessionState -> if (message.state == SessionStates.NONE) return end(session, EndReasons.PEER_LOST)
                else -> Unit
            }
            is CarEvent.Door -> return door(session, phase, event)
            else -> Unit
        }
        if (event !is CarEvent.Distance && event !is CarEvent.GearChanged) return session to emptyList()

        val distance = session.distanceM
        if (distance == null || distance > NEARBY_EXIT_METERS || session.gear != Gear.PARK) {
            val (restored, effects) = restoreLight(session, sendToServer = true)
            return restored.copy(phase = Phase.Sharing(phase.sessionId, phase.riderName)) to effects
        }
        return session to emptyList()
    }

    private fun door(session: CarSession, phase: Phase.Nearby, event: CarEvent.Door): Pair<CarSession, List<CarEffect>> {
        if (event.open) {
            return session.copy(phase = phase.copy(openedDoors = phase.openedDoors + event.door)) to emptyList()
        }
        val distance = session.distanceM
        val boarded = event.door in phase.openedDoors && session.gear == Gear.PARK &&
            distance != null && distance <= BOARDING_MAX_METERS
        if (!boarded) return session to emptyList()
        val text = "${phase.riderName} 탑승 확인, 위치공유를 끝낼게요"
        return end(session, EndReasons.BOARDED, listOf(Send(SessionBoarded)), listOf(Notify(text), Speak(text)))
    }

    private fun ended(session: CarSession, event: CarEvent): Pair<CarSession, List<CarEffect>> = when (event) {
        CarEvent.EndedTimeout -> session.copy(phase = Phase.Idle) to emptyList()
        is CarEvent.Server -> (event.received.message as? PickupRequested)
            ?.let { request(session, event.received.sessionId, it.riderName) }
            ?: (session to emptyList())
        else -> session to emptyList()
    }

    private fun request(session: CarSession, sessionId: String?, riderName: String): Pair<CarSession, List<CarEffect>> =
        newSession(session, Phase.Requested(sessionId, riderName)) to listOf(
            ShowRequestAlert(riderName),
            Speak("${riderName.toSubject()} 데리러 와 달래요. 갈까요?"),
            Listen,
        )

    /** 새 세션은 길안내 판단·탑승자 좌표를 비우고 시작한다. 실내등 원래 값은 남긴다(앱 재시작 복원값일 수 있다) */
    private fun newSession(session: CarSession, phase: Phase) =
        session.copy(phase = phase, peer = null, distanceM = null, navStarted = false, navDecided = false)

    /**
     * 종료 공통: 앞쪽 효과(서버에 보낼 메시지) → 요청 알림 닫기 → 실내등 복구(서버에 안 보냄) → 위치 전송 중지 → 길안내 취소 → 뒤쪽 효과(안내).
     * 서버는 SHARING에서만 vehicle.light를 받으므로 종료 때 실내등 복구는 알리지 않는다.
     */
    private fun end(
        session: CarSession,
        reason: String,
        before: List<CarEffect> = emptyList(),
        after: List<CarEffect> = emptyList(),
    ): Pair<CarSession, List<CarEffect>> {
        val effects = before.toMutableList()
        if (session.phase is Phase.Requested) effects += DismissRequestAlert
        val (restored, lightEffects) = restoreLight(session, sendToServer = false)
        effects += lightEffects
        if (session.phase is Phase.Sharing || session.phase is Phase.Nearby) effects += StopLocationSharing
        if (session.navStarted) effects += StopNavigation
        effects += after
        return restored.copy(phase = Phase.Ended(reason), navStarted = false) to effects
    }

    private fun restoreLight(session: CarSession, sendToServer: Boolean): Pair<CarSession, List<CarEffect>> {
        val before = session.lightBefore ?: return session to emptyList()
        val effects = mutableListOf<CarEffect>(SetCabinLight(before), SaveLightBefore(null))
        if (sendToServer) effects += Send(VehicleLight(on = before == CabinSwitch.ON))
        return session.copy(lightBefore = null) to effects
    }
}
