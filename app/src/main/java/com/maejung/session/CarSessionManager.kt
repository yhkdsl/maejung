package com.maejung.session

import android.location.Location
import android.util.Log
import com.maejung.alert.PickupAlerts
import com.maejung.location.Fix
import com.maejung.navi.Navigator
import com.maejung.location.LocationSource
import com.maejung.net.ApiClient
import com.maejung.net.ConnectionState
import com.maejung.net.SessionTransport
import com.maejung.protocol.LocationUpdate
import com.maejung.protocol.PairingCodeIssued
import com.maejung.protocol.PairingCompleted
import com.maejung.protocol.PeerLocation
import com.maejung.protocol.SessionState
import com.maejung.protocol.SessionStates
import com.maejung.store.FamilyMember
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
    /** null이면 가짜 서버로 돈다(등록·연결 코드 없음) */
    private val api: ApiClient?,
    private val alerts: PickupAlerts,
    private val navigator: Navigator,
) {
    private val events = Channel<CarEvent>(Channel.UNLIMITED)
    private val _session = MutableStateFlow(CarSession(lightBefore = prefs.lightBefore))
    val session: StateFlow<CarSession> = _session.asStateFlow()

    private val _carFix = MutableStateFlow<Fix?>(null)
    val carFix: StateFlow<Fix?> = _carFix.asStateFlow()

    val connection: StateFlow<ConnectionState> = transport.connection

    private val _family = MutableStateFlow(prefs.family)
    val family: StateFlow<List<FamilyMember>> = _family.asStateFlow()

    /** 새 가족이 연결되면 홈 화면에 잠깐 보여줄 이름 */
    private val _pairedNotice = MutableStateFlow<String?>(null)
    val pairedNotice: StateFlow<String?> = _pairedNotice.asStateFlow()

    private var peer: PeerLocation? = null
    private var sharingJob: Job? = null
    private var endedJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch { for (event in events) process(event) }
        scope.launch {
            // 4401: 서버 비밀키가 바뀌어 토큰이 무효다. 다시 등록하고, 이전 가족 연결은 지운다(PROTOCOL.md 3절)
            transport.connection.collect {
                if (it == ConnectionState.UNAUTHORIZED && api != null) {
                    Log.w(TAG, "토큰 무효, 차를 다시 등록합니다")
                    prefs.clearVehicle()
                    _family.value = emptyList()
                    transport.connect(registerVehicle())
                }
            }
        }
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
        scope.launch {
            val token = if (api == null) "fake" else prefs.vehicleToken ?: registerVehicle()
            transport.connect(token)
        }
    }

    /** 서버가 꺼져 있으면 붙을 때까지 5초마다 다시 시도한다 */
    private suspend fun registerVehicle(): String {
        val api = requireNotNull(api)
        while (true) {
            val registered = runCatching { api.registerVehicle() }
                .onFailure { Log.w(TAG, "차 등록 실패, 5초 뒤 다시: ${it.message}") }
                .getOrNull()
            if (registered != null) {
                prefs.vehicleToken = registered.vehicleToken
                Log.i(TAG, "차 등록 완료 ${registered.vehicleId}")
                return registered.vehicleToken
            }
            delay(REGISTER_RETRY_MS)
        }
    }

    /** 홈 화면 "가족 연결" 버튼. 6자리 코드와 만료 시각(밀리초) */
    suspend fun issuePairingCode(): PairingCodeIssued? {
        val api = api ?: return null
        val token = prefs.vehicleToken ?: return null
        return runCatching { api.issuePairingCode(token) }
            .onFailure { Log.w(TAG, "연결 코드 발급 실패: ${it.message}") }
            .getOrNull()
    }

    fun clearPairedNotice() {
        _pairedNotice.value = null
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
        // 가족 연결은 세션과 관계없어서 머신을 거치지 않는다
        if (message is PairingCompleted) {
            prefs.addFamily(FamilyMember(message.riderId, message.riderName))
            _family.value = prefs.family
            _pairedNotice.value = message.riderName
        }
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
            CarEffect.Listen -> listenForAnswer()
            CarEffect.StartLocationSharing -> startSharing()
            CarEffect.StopLocationSharing -> stopSharing()
            is CarEffect.ShowRequestAlert -> alerts.showRequest(effect.riderName)
            CarEffect.DismissRequestAlert -> alerts.dismissRequest()
            is CarEffect.Notify -> alerts.notify(effect.text)
            is CarEffect.StartNavigation -> navigator.start(effect.lat, effect.lng, effect.riderName)
            CarEffect.StopNavigation -> navigator.stop()
        }
    }

    /** 요청 음성 뒤에 한 번 듣고 "응/아니"면 수락·거절한다. 그사이 버튼으로 답했으면 머신이 무시한다 */
    private fun listenForAnswer() {
        scope.launch {
            when (VoiceAnswers.parse(voice.listen())) {
                VoiceAnswer.ACCEPT -> events.send(CarEvent.UserAccept)
                VoiceAnswer.REJECT -> events.send(CarEvent.UserReject)
                null -> Unit
            }
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
        const val REGISTER_RETRY_MS = 5_000L
    }
}
