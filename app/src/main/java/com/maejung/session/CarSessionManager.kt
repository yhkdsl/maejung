package com.maejung.session

import android.location.Location
import android.util.Log
import com.maejung.location.Fix
import com.maejung.location.LocationSource
import com.maejung.net.SessionTransport
import com.maejung.protocol.LocationUpdate
import com.maejung.protocol.PeerLocation
import com.maejung.protocol.SessionState
import com.maejung.protocol.SessionStates
import com.maejung.store.Prefs
import com.maejung.vehicle.VehicleSignals
import com.maejung.voice.VoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 상태 머신에 이벤트를 넣고 나온 효과를 실행한다(docs/CAR_APP.md 2·3절). Application 범위에 하나만 둔다.
 * 이벤트는 채널 하나로 순서대로 처리한다. 효과 실행 중에 생긴 이벤트(거리 등)도 같은 채널을 거친다.
 */
class CarSessionManager(
    private val scope: CoroutineScope,
    private val transport: SessionTransport,
    private val vehicle: VehicleSignals,
    private val location: LocationSource,
    private val voice: VoiceIO,
    private val prefs: Prefs,
) {
    private val events = Channel<CarEvent>(Channel.UNLIMITED)
    private val _session = MutableStateFlow(CarSession(lightBefore = prefs.lightBefore))
    val session: StateFlow<CarSession> = _session.asStateFlow()

    private val _carFix = MutableStateFlow<Fix?>(null)
    val carFix: StateFlow<Fix?> = _carFix.asStateFlow()

    private var peer: PeerLocation? = null
    private var sharingJob: Job? = null
    private var endedJob: Job? = null
    private var started = false

    fun start(token: String) {
        if (started) return
        started = true
        scope.launch { for (event in events) process(event) }
        scope.launch { transport.incoming.collect { events.send(CarEvent.Server(it)) } }
        scope.launch { vehicle.gear.collect { events.send(CarEvent.GearChanged(it)) } }
        scope.launch { vehicle.doors.collect { events.send(CarEvent.Door(it.door, it.open)) } }
        scope.launch {
            location.fixes.collect {
                _carFix.value = it
                events.send(CarEvent.Distance(distance()))
            }
        }
        scope.launch { events.send(CarEvent.CabinLight(vehicle.readCabinLight())) }
        location.start()
        transport.connect(token)
    }

    /** 위치 퍼미션을 늦게 받았을 때 다시 구독한다 */
    fun restartLocation() = location.start()

    fun accept() = events.trySend(CarEvent.UserAccept)

    fun reject() = events.trySend(CarEvent.UserReject)

    fun end() = events.trySend(CarEvent.UserEnd)

    private suspend fun process(event: CarEvent) {
        val before = _session.value
        val (next, effects) = CarSessionMachine.reduce(before, event)
        _session.value = next
        if (event !is CarEvent.Distance && event !is CarEvent.GearChanged || before.phase != next.phase || effects.isNotEmpty()) {
            Log.i(TAG, "${event.label()} : ${before.phase.label()} → ${next.phase.label()} ${effects.map { it.label() }}")
        }
        effects.forEach { execute(it) }
        restoreLeftoverLight(event)
        if (next.phase is Phase.Ended && before.phase !is Phase.Ended) {
            endedJob?.cancel()
            endedJob = scope.launch {
                delay(ENDED_SHOW_MS)
                events.send(CarEvent.EndedTimeout)
            }
        }
        // 탑승자 위치가 오면 거리를 다시 계산해 넣는다(머신은 거리를 이벤트로만 받는다)
        val message = (event as? CarEvent.Server)?.received?.message
        if (message is PeerLocation) {
            peer = message
            events.send(CarEvent.Distance(distance()))
        }
    }

    private suspend fun execute(effect: CarEffect) {
        when (effect) {
            is CarEffect.Send -> transport.send(effect.message)
            is CarEffect.SetCabinLight -> vehicle.setCabinLight(effect.value)
            is CarEffect.SaveLightBefore -> prefs.lightBefore = effect.value
            is CarEffect.Speak -> voice.speak(effect.text)
            CarEffect.Listen -> Unit // 음성 인식은 A6·A8에서 붙인다
            CarEffect.StartLocationSharing -> startSharing()
            CarEffect.StopLocationSharing -> stopSharing()
            // 헤드업 알림·길안내는 A8에서 붙인다. 지금은 화면(RequestScreen·SharingScreen)과 로그로만 보인다
            is CarEffect.ShowRequestAlert, CarEffect.DismissRequestAlert, is CarEffect.Notify,
            is CarEffect.StartNavigation, CarEffect.StopNavigation -> Unit
        }
    }

    /**
     * 앱이 Nearby 도중 죽었다 살아났는데 서버에 진행 중 세션이 없으면(session.state none),
     * Prefs에 남은 원래 값으로 실내등을 되돌린다. 머신은 Idle에서 이 메시지를 무시하므로 여기서 처리한다.
     */
    private suspend fun restoreLeftoverLight(event: CarEvent) {
        val state = (event as? CarEvent.Server)?.received?.message as? SessionState ?: return
        val current = _session.value
        val leftover = current.lightBefore ?: return
        if (state.state != SessionStates.NONE || current.phase !is Phase.Idle) return
        vehicle.setCabinLight(leftover)
        prefs.lightBefore = null
        _session.value = current.copy(lightBefore = null)
        Log.i(TAG, "남아 있던 실내등 원래 값 $leftover 로 복구")
    }

    /** heartbeat: 3초마다 마지막 위치를 보낸다(PROTOCOL.md 3.2의 "3초 또는 10m, 최소 10초"를 3초 간격으로 만족) */
    private fun startSharing() {
        sharingJob?.cancel()
        peer = null
        sharingJob = scope.launch {
            events.send(CarEvent.CabinLight(vehicle.readCabinLight()))
            while (isActive) {
                _carFix.value?.let { transport.send(LocationUpdate(it.lat, it.lng, it.accuracy)) }
                delay(HEARTBEAT_MS)
            }
        }
    }

    private fun stopSharing() {
        sharingJob?.cancel()
        sharingJob = null
        peer = null
    }

    private fun distance(): Double? {
        val car = _carFix.value ?: return null
        val rider = peer ?: return null
        val result = FloatArray(1)
        Location.distanceBetween(car.lat, car.lng, rider.lat, rider.lng, result)
        return result[0].toDouble()
    }

    private fun CarEvent.label() = when (this) {
        is CarEvent.Server -> received.message::class.simpleName
        is CarEvent.Distance -> "Distance(${meters?.toInt()}m)"
        else -> toString()
    }

    private fun Phase.label() = this::class.simpleName

    private fun CarEffect.label() = when (this) {
        is CarEffect.Send -> "Send(${message::class.simpleName})"
        else -> toString()
    }

    private companion object {
        const val TAG = "CarSession"
        const val HEARTBEAT_MS = 3_000L
        const val ENDED_SHOW_MS = 3_000L
    }
}
