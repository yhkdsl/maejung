package com.maejung.phone.session

import com.maejung.phone.location.LocationFix
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
import com.maejung.protocol.LocationUpdate
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

/** Application-owned session state and Sharing-only location heartbeat. */
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
    private var appStopped = false
    private var pairingJob: Job? = null
    private var pairingAttempt = 0L
    private var requestTimeout: Job? = null
    private var requestAttempt = 0L
    private var endedTimer: Job? = null
    private var endedAttempt = 0L
    private var locationHeartbeat: Job? = null
    private var locationAttempt = 0L

    init {
        // A replay-free transport may emit its initial snapshot from connect itself.
        managerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.incoming.collect { received ->
                synchronized(lock) { if (!closed && !appStopped) onReceived(received) }
            }
        }
        managerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.connection.collect {
                synchronized(lock) { if (!closed && !appStopped) onConnectionChanged() }
            }
        }
        synchronized(lock) {
            if (mutableState.value.pairing != null) connectLocked()
        }
    }

    fun pair(code: String, riderName: String) {
        synchronized(lock) {
            val current = mutableState.value
            if (closed || appStopped || current.phase != PhonePhase.UNPAIRED || current.pairingInProgress) return
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
            if (closed || appStopped || !current.canRequest || transport.connection.value != ConnectionState.CONNECTED) return
            cancelRequestTimeout()
            cancelLocationHeartbeat()
            mutableState.value = current.copy(
                phase = PhonePhase.REQUESTING, sessionId = null, error = null, endReason = null,
                peerLocation = null, eta = null, lightOn = null, ownLocation = null,
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
                    if (!closed && !appStopped && attempt == requestAttempt &&
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
            if (closed || appStopped || !current.isActive) return
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

    fun updateOwnLocation(fix: LocationFix) {
        synchronized(lock) {
            val current = mutableState.value
            if (closed || appStopped || current.phase != PhonePhase.SHARING ||
                current.pairing == null || current.sessionId.isNullOrBlank() ||
                transport.connection.value in setOf(ConnectionState.REPLACED, ConnectionState.UNAUTHORIZED) ||
                !validOwnLocation(fix)
            ) return
            mutableState.value = current.copy(ownLocation = fix)
            startLocationHeartbeat()
        }
    }

    fun clearOwnLocation() {
        synchronized(lock) {
            if (closed) return
            cancelLocationHeartbeat()
            mutableState.value = mutableState.value.copy(ownLocation = null)
        }
    }

    fun setLocationNotice(message: String?) {
        synchronized(lock) {
            if (closed || appStopped) return
            mutableState.value = mutableState.value.copy(locationNotice = message)
        }
    }

    /** Task removal is best effort, with no queued end or automatic socket retry. */
    fun appClosed() {
        synchronized(lock) {
            if (closed || appStopped) return
            appStopped = true
            val current = mutableState.value
            pairingAttempt++
            pairingJob?.cancel()
            cancelRequestTimeout()
            cancelEndedTimer()
            cancelLocationHeartbeat()
            if (current.isActive && current.synchronized &&
                current.connection == ConnectionState.CONNECTED && transport.connection.value == ConnectionState.CONNECTED
            ) {
                runCatching { transport.send(SessionEnd(EndReasons.APP_CLOSED)) }
            }
            if (current.isActive) enterEnded(EndReasons.APP_CLOSED)
            mutableState.value = mutableState.value.copy(
                connection = ConnectionState.DISCONNECTED, synchronized = false, pairingInProgress = false,
                ownLocation = null, locationNotice = null, peerLocation = null, eta = null, lightOn = null,
            )
            transport.close()
        }
    }

    fun reconnect() {
        synchronized(lock) {
            if (closed) return
            appStopped = false
            if (mutableState.value.pairing == null) return
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
            cancelLocationHeartbeat()
            mutableState.value = mutableState.value.copy(
                pairingInProgress = false, ownLocation = null, locationNotice = null,
            )
            managerJob.cancel()
            transport.close()
        }
    }

    private fun connectLocked() {
        if (closed || appStopped) return
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
            cancelLocationHeartbeat()
            store.clear()
            mutableState.value = LivePhoneState(
                connection = ConnectionState.UNAUTHORIZED,
                error = "연결 정보를 확인할 수 없어요. 코드를 다시 입력해 주세요",
            )
            transport.close()
            return
        }
        val current = mutableState.value
        if (connection == ConnectionState.REPLACED) cancelLocationHeartbeat()
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
                if (clearTelemetry) cancelLocationHeartbeat()
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
                    ownLocation = if (clearTelemetry) null else current.ownLocation,
                )
                if (sharing) startLocationHeartbeat()
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

    private fun validOwnLocation(fix: LocationFix): Boolean =
        fix.lat.isFinite() && fix.lat in -90.0..90.0 &&
            fix.lng.isFinite() && fix.lng in -180.0..180.0 &&
            fix.accuracy.isFinite() && fix.accuracy >= 0f &&
            (fix.courseDegrees == null || fix.courseDegrees.isFinite())

    private fun startLocationHeartbeat() {
        val current = mutableState.value
        val sessionId = current.sessionId?.takeIf { it.isNotBlank() } ?: return
        if (closed || appStopped || current.phase != PhonePhase.SHARING || current.ownLocation == null ||
            locationHeartbeat?.isActive == true
        ) return
        val attempt = locationAttempt
        sendOwnLocation(sessionId)
        // A synchronous transport callback may already have ended or replaced this session.
        if (attempt != locationAttempt || closed || appStopped) return
        locationHeartbeat = managerScope.launch {
            while (true) {
                delay(3_000)
                synchronized(lock) {
                    val latest = mutableState.value
                    if (closed || appStopped || attempt != locationAttempt || latest.sessionId != sessionId ||
                        latest.phase != PhonePhase.SHARING || latest.ownLocation == null
                    ) return@launch
                    sendOwnLocation(sessionId)
                }
            }
        }
    }

    private fun sendOwnLocation(sessionId: String) {
        val current = mutableState.value
        val fix = current.ownLocation ?: return
        if (!closed && !appStopped && current.phase == PhonePhase.SHARING && current.pairing != null &&
            current.sessionId == sessionId && current.synchronized && current.connection == ConnectionState.CONNECTED &&
            transport.connection.value == ConnectionState.CONNECTED
        ) {
            transport.send(LocationUpdate(fix.lat, fix.lng, fix.accuracy))
        }
    }

    private fun cancelLocationHeartbeat() {
        locationAttempt++
        locationHeartbeat?.cancel()
        locationHeartbeat = null
    }

    private fun enterEnded(reason: String) {
        cancelRequestTimeout()
        cancelEndedTimer()
        cancelLocationHeartbeat()
        mutableState.value = mutableState.value.copy(
            phase = PhonePhase.ENDED, error = null, endReason = reason,
            peerLocation = null, eta = null, lightOn = null, ownLocation = null, locationNotice = null,
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
        cancelLocationHeartbeat()
        mutableState.value = mutableState.value.copy(
            phase = PhonePhase.IDLE, sessionId = null, error = error, endReason = null,
            synchronized = mutableState.value.synchronized && !requireSnapshot,
            peerLocation = null, eta = null, lightOn = null, ownLocation = null, locationNotice = null,
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
