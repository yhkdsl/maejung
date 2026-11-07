package com.maejung.phone.session

import com.maejung.phone.net.ApiException
import com.maejung.phone.net.ConnectionState
import com.maejung.phone.net.PairingApi
import com.maejung.phone.net.SessionTransport
import com.maejung.phone.store.PairingStore
import com.maejung.phone.store.StoredPairing
import com.maejung.protocol.EndReasons
import com.maejung.protocol.ErrorCodes
import com.maejung.protocol.ErrorMessage
import com.maejung.protocol.EtaSources
import com.maejung.protocol.PeerEta
import com.maejung.protocol.PeerLight
import com.maejung.protocol.PeerLocation
import com.maejung.protocol.PickupAccepted
import com.maejung.protocol.PickupPending
import com.maejung.protocol.PickupRequest
import com.maejung.protocol.Received
import com.maejung.protocol.SessionEnd
import com.maejung.protocol.SessionEnded
import com.maejung.protocol.SessionState
import com.maejung.protocol.SessionStates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Application-owned P3 session state. GPS and location-service lifecycle belong to P4. */
class PhoneSessionManager(
    private val api: PairingApi,
    private val store: PairingStore,
    private val transport: SessionTransport,
    scope: CoroutineScope,
) {
    private val lock = Any()
    private val managerJob = SupervisorJob(scope.coroutineContext[Job])
    private val managerScope = CoroutineScope(scope.coroutineContext + managerJob)
    private val restoredPairing = store.load()
    private val mutableState = MutableStateFlow(
        LivePhoneState(
            phase = if (restoredPairing == null) PhonePhase.UNPAIRED else PhonePhase.IDLE,
            pairing = restoredPairing,
            connection = transport.connection.value,
        ),
    )
    val state: StateFlow<LivePhoneState> = mutableState.asStateFlow()

    private var closed = false
    private var pairingJob: Job? = null
    private var pairingAttempt = 0L
    private var requestTimeout: Job? = null
    private var requestAttempt = 0L
    private var endedTimer: Job? = null
    private var endedAttempt = 0L

    init {
        // A replay-free transport may emit its initial snapshot from connect itself.
        managerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.incoming.collect { received ->
                synchronized(lock) { if (!closed) onReceived(received) }
            }
        }
        managerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.connection.collect {
                synchronized(lock) { if (!closed) onConnectionChanged() }
            }
        }
        synchronized(lock) {
            if (mutableState.value.pairing != null) connectLocked()
        }
    }

    fun pair(code: String, riderName: String) {
        synchronized(lock) {
            val current = mutableState.value
            if (closed || current.phase != PhonePhase.UNPAIRED || current.pairingInProgress) return
            val name = riderName.trim()
            if (code.length != 6 || code.any { it !in '0'..'9' } || name.length !in 1..20) {
                mutableState.value = current.copy(error = CODE_PROMPT)
                return
            }
            val attempt = ++pairingAttempt
            mutableState.value = current.copy(pairingInProgress = true, error = null)
            pairingJob = managerScope.launch {
                try {
                    val result = api.pair(code, name)
                    synchronized(lock) response@{
                        if (closed || attempt != pairingAttempt) return@response
                        val pairing = StoredPairing(result.riderId, result.vehicleId, result.riderToken, name)
                        store.save(pairing)
                        mutableState.value = LivePhoneState(phase = PhonePhase.IDLE, pairing = pairing)
                        connectLocked()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    synchronized(lock) failure@{
                        if (closed || attempt != pairingAttempt) return@failure
                        val prompt = if (error is ApiException && error.status in setOf(400, 404)) {
                            CODE_PROMPT
                        } else {
                            CONNECTION_PROMPT
                        }
                        mutableState.value = mutableState.value.copy(pairingInProgress = false, error = prompt)
                    }
                }
            }
        }
    }

    fun requestPickup() {
        synchronized(lock) {
            val current = mutableState.value
            if (closed || !current.canRequest || transport.connection.value != ConnectionState.CONNECTED) return
            cancelRequestTimeout()
            mutableState.value = current.copy(
                phase = PhonePhase.REQUESTING, sessionId = null, error = null, endReason = null,
                peerLocation = null, eta = null, lightOn = null,
            )
            if (!transport.send(PickupRequest(requireNotNull(current.pairing).riderName))) {
                returnIdle(CONNECTION_PROMPT)
                return
            }
            // A synchronous response can already have bound or restored the session.
            if (mutableState.value.phase != PhonePhase.REQUESTING || mutableState.value.sessionId != null) return
            val attempt = requestAttempt
            requestTimeout = managerScope.launch {
                delay(10_000)
                synchronized(lock) {
                    if (!closed && attempt == requestAttempt &&
                        mutableState.value.phase == PhonePhase.REQUESTING && mutableState.value.sessionId == null
                    ) {
                        returnIdle(CONNECTION_PROMPT, requireSnapshot = true)
                        if (transport.connection.value !in setOf(ConnectionState.REPLACED, ConnectionState.UNAUTHORIZED)) {
                            connectLocked()
                        }
                    }
                }
            }
        }
    }

    fun endSession() {
        synchronized(lock) {
            val current = mutableState.value
            if (closed || !current.isActive) return
            // A failed end is not queued: keep the real session visible until the user retries.
            if (!current.synchronized || transport.connection.value != ConnectionState.CONNECTED ||
                !transport.send(SessionEnd(EndReasons.MANUAL))
            ) {
                mutableState.value = current.copy(error = END_RETRY_PROMPT)
                return
            }
            enterEnded(EndReasons.MANUAL)
        }
    }

    fun reconnect() {
        synchronized(lock) {
            if (closed || mutableState.value.pairing == null) return
            connectLocked()
        }
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            pairingAttempt++
            cancelRequestTimeout()
            cancelEndedTimer()
            mutableState.value = mutableState.value.copy(pairingInProgress = false)
            managerJob.cancel()
            transport.close()
        }
    }

    private fun connectLocked() {
        val pairing = mutableState.value.pairing ?: return
        // Reconnect attempts must preserve an unbound request's original acknowledgement deadline.
        mutableState.value = mutableState.value.copy(
            connection = ConnectionState.CONNECTING, synchronized = false,
        )
        transport.connect(pairing.riderToken)
    }

    private fun onConnectionChanged() {
        // Read the latest StateFlow value rather than a queued, obsolete notification.
        val connection = transport.connection.value
        if (connection == ConnectionState.UNAUTHORIZED) {
            pairingAttempt++
            pairingJob?.cancel()
            cancelRequestTimeout()
            cancelEndedTimer()
            store.clear()
            mutableState.value = LivePhoneState(
                connection = ConnectionState.UNAUTHORIZED,
                error = "연결 정보를 확인할 수 없어요. 코드를 다시 입력해 주세요",
            )
            transport.close()
            return
        }
        val current = mutableState.value
        mutableState.value = current.copy(
            connection = connection,
            // CONNECTED may be observed after its snapshot; preserve synchronization in that case.
            synchronized = current.synchronized && connection == ConnectionState.CONNECTED,
        )
    }

    private fun onReceived(received: Received) {
        val current = mutableState.value
        if (current.pairing == null || transport.connection.value != ConnectionState.CONNECTED) return
        when (val message = received.message) {
            is SessionState -> applySnapshot(message, received.sessionId)
            PickupPending -> {
                val id = received.sessionId?.takeIf { it.isNotBlank() } ?: return
                if (!current.synchronized || current.phase != PhonePhase.REQUESTING ||
                    (current.sessionId != null && current.sessionId != id)
                ) return
                cancelRequestTimeout()
                mutableState.value = current.copy(sessionId = id, error = null)
            }
            PickupAccepted -> {
                if (!current.synchronized || current.phase != PhonePhase.REQUESTING ||
                    !matchesSession(received.sessionId)
                ) return
                cancelRequestTimeout()
                mutableState.value = current.copy(phase = PhonePhase.SHARING, error = null)
            }
            is PeerLocation -> if (canReceiveTelemetry(received.sessionId) && validLocation(message)) {
                mutableState.value = current.copy(peerLocation = message)
            }
            is PeerEta -> if (canReceiveTelemetry(received.sessionId) && message.seconds >= 0 &&
                message.meters >= 0 && message.source in setOf(EtaSources.NAVI, EtaSources.ESTIMATE)
            ) {
                mutableState.value = current.copy(eta = message)
            }
            is PeerLight -> if (canReceiveTelemetry(received.sessionId)) {
                mutableState.value = current.copy(lightOn = message.on)
            }
            is SessionEnded -> if (current.isActive && matchesSession(received.sessionId)) {
                enterEnded(message.reason)
            }
            is ErrorMessage -> {
                if (current.phase != PhonePhase.REQUESTING) return
                when (message.code) {
                    ErrorCodes.VEHICLE_OFFLINE -> returnIdle("차가 꺼져 있어요")
                    ErrorCodes.SESSION_ALREADY_ACTIVE -> returnIdle("이미 다른 요청이 진행 중이에요")
                    else -> Unit
                }
            }
            else -> Unit
        }
    }

    private fun applySnapshot(snapshot: SessionState, sessionId: String?) {
        val current = mutableState.value
        when (snapshot.state) {
            SessionStates.NONE -> {
                if (sessionId != null) return
                cancelRequestTimeout()
                mutableState.value = current.copy(connection = ConnectionState.CONNECTED, synchronized = true)
                when {
                    current.isActive -> enterEnded(EndReasons.PEER_LOST)
                    current.phase == PhonePhase.ENDED -> Unit
                    else -> returnIdle()
                }
            }
            SessionStates.REQUESTED, SessionStates.SHARING -> {
                if (sessionId.isNullOrBlank()) return
                if (snapshot.riderId != null && snapshot.riderId != current.pairing?.riderId) return
                cancelRequestTimeout()
                cancelEndedTimer()
                val sharing = snapshot.state == SessionStates.SHARING
                val clearTelemetry = current.sessionId != sessionId || !sharing
                mutableState.value = current.copy(
                    phase = if (sharing) PhonePhase.SHARING else PhonePhase.REQUESTING,
                    connection = ConnectionState.CONNECTED,
                    synchronized = true,
                    sessionId = sessionId,
                    error = null,
                    endReason = null,
                    peerLocation = if (clearTelemetry) null else current.peerLocation,
                    eta = if (clearTelemetry) null else current.eta,
                    lightOn = if (clearTelemetry) null else current.lightOn,
                )
            }
            else -> Unit
        }
    }

    private fun matchesSession(id: String?): Boolean =
        !id.isNullOrBlank() && id == mutableState.value.sessionId

    private fun canReceiveTelemetry(id: String?): Boolean =
        mutableState.value.phase == PhonePhase.SHARING && mutableState.value.synchronized && matchesSession(id)

    private fun validLocation(location: PeerLocation): Boolean =
        location.lat.isFinite() && location.lat in -90.0..90.0 &&
            location.lng.isFinite() && location.lng in -180.0..180.0 &&
            location.accuracy.isFinite() && location.accuracy >= 0f

    private fun enterEnded(reason: String) {
        cancelRequestTimeout()
        cancelEndedTimer()
        mutableState.value = mutableState.value.copy(
            phase = PhonePhase.ENDED, error = null, endReason = reason,
            peerLocation = null, eta = null, lightOn = null,
        )
        val attempt = endedAttempt
        endedTimer = managerScope.launch {
            delay(3_000)
            synchronized(lock) {
                if (!closed && attempt == endedAttempt && mutableState.value.phase == PhonePhase.ENDED) {
                    returnIdle()
                }
            }
        }
    }

    private fun returnIdle(error: String? = null, requireSnapshot: Boolean = false) {
        cancelRequestTimeout()
        cancelEndedTimer()
        mutableState.value = mutableState.value.copy(
            phase = PhonePhase.IDLE, sessionId = null, error = error, endReason = null,
            synchronized = mutableState.value.synchronized && !requireSnapshot,
            peerLocation = null, eta = null, lightOn = null,
        )
    }

    private fun cancelRequestTimeout() {
        requestAttempt++
        requestTimeout?.cancel()
        requestTimeout = null
    }

    private fun cancelEndedTimer() {
        endedAttempt++
        endedTimer?.cancel()
        endedTimer = null
    }

    private companion object {
        const val CODE_PROMPT = "코드를 다시 확인해 주세요"
        const val CONNECTION_PROMPT = "연결을 확인해 주세요"
        const val END_RETRY_PROMPT = "종료 요청을 보내지 못했어요. 연결 후 다시 시도해 주세요"
    }
}
