package com.maejung.net

import com.maejung.protocol.ClientMessage
import com.maejung.protocol.PeerLocation
import com.maejung.protocol.PickupAccept
import com.maejung.protocol.PickupReject
import com.maejung.protocol.PickupRequested
import com.maejung.protocol.Received
import com.maejung.protocol.ServerMessage
import com.maejung.protocol.SessionBoarded
import com.maejung.protocol.SessionEnd
import com.maejung.protocol.SessionState
import com.maejung.protocol.SessionStates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 서버 없이 흐름을 돌려 보는 가짜(A4). 요청을 흉내 내고, 수락하면 탑승자가 학원 출입구에서 차까지 걸어온다.
 * 좌표는 데모 시나리오(docs/TASKS.md D1): 학원 37.49830, 127.06280 → 차 37.49950, 127.06030(약 260m).
 */
class FakeSessionTransport(private val scope: CoroutineScope) : SessionTransport {
    private val _incoming = MutableSharedFlow<Received>(extraBufferCapacity = 64)
    override val incoming: Flow<Received> = _incoming.asSharedFlow()

    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private var walk: Job? = null
    private var seq = 0

    override fun connect(token: String) {
        _connection.value = ConnectionState.CONNECTED
        emit(SessionState(SessionStates.NONE), sessionId = null)
    }

    /** 홈 화면의 디버그 버튼 */
    fun simulateRequest(riderName: String = "민지") {
        seq += 1
        emit(PickupRequested("r_fake", riderName))
    }

    override fun send(message: ClientMessage) {
        when (message) {
            PickupAccept -> walk = scope.launch { walkToCar() }
            PickupReject, SessionBoarded, is SessionEnd -> walk?.cancel()
            else -> Unit
        }
    }

    override fun close() {
        walk?.cancel()
        _connection.value = ConnectionState.DISCONNECTED
    }

    private suspend fun walkToCar() {
        for (step in 0..STEPS) {
            val t = step.toDouble() / STEPS
            emit(PeerLocation(lerp(ACADEMY_LAT, CAR_LAT, t), lerp(ACADEMY_LNG, CAR_LNG, t), 5f))
            delay(STEP_MS)
        }
    }

    private fun emit(message: ServerMessage, sessionId: String? = "s_fake_$seq") {
        _incoming.tryEmit(Received(message, sessionId, System.currentTimeMillis()))
    }

    private fun lerp(from: Double, to: Double, t: Double) = from + (to - from) * t

    companion object {
        const val ACADEMY_LAT = 37.49830
        const val ACADEMY_LNG = 127.06280
        const val CAR_LAT = 37.49950
        const val CAR_LNG = 127.06030
        private const val STEPS = 15
        private const val STEP_MS = 2_000L
    }
}
