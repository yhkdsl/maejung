package com.maejung.phone.ui

import com.maejung.phone.net.ConnectionState
import com.maejung.phone.session.LivePhoneState
import com.maejung.phone.session.PhonePhase
import com.maejung.phone.session.PhoneState
import com.maejung.phone.session.SharingInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PhoneUiMode { LIVE, PREVIEW }
enum class LivePhoneScreen { PAIR, HOME, REQUESTING, SHARING, ENDED }

/** UI-only draft and explicit preview selection; no tokens or manager lifecycle. */
data class LivePhoneUiState(
    val mode: PhoneUiMode = PhoneUiMode.LIVE,
    val name: String = "",
    val code: String = "",
    val validationShown: Boolean = false,
)

/** Routes UI actions without owning the Application's connection or session lifecycle. */
class LivePhoneController(
    private val liveState: () -> LivePhoneState,
    private val previewEnabled: Boolean,
    private val onPair: (String, String) -> Unit,
    private val onRequestPickup: () -> Unit,
    private val onEndSession: () -> Unit,
    private val onReconnect: () -> Unit,
    initialName: String = "",
    initialCode: String = "",
    validationShown: Boolean = false,
) {
    private val mutableState = MutableStateFlow(LivePhoneUiState(
        name = initialName, code = initialCode, validationShown = validationShown,
    ))
    val state: StateFlow<LivePhoneUiState> = mutableState.asStateFlow()

    fun updateName(value: String) { mutableState.value = state.value.copy(name = value) }
    fun updateCode(value: String) { mutableState.value = state.value.copy(code = value) }

    fun enterPreview(): Boolean {
        val live = liveState()
        if (!previewEnabled || live.isActive || live.pairingInProgress) return false
        mutableState.value = state.value.copy(mode = PhoneUiMode.PREVIEW)
        return true
    }

    fun returnToLive() { mutableState.value = state.value.copy(mode = PhoneUiMode.LIVE) }

    fun refreshLiveState() {
        if (liveState().isActive || liveState().pairingInProgress) returnToLive()
    }

    fun pair() {
        val live = liveState()
        if (state.value.mode != PhoneUiMode.LIVE || live.phase != PhonePhase.UNPAIRED || live.pairingInProgress) return
        mutableState.value = state.value.copy(validationShown = true)
        val draft = state.value
        // Reuse the P2/server-compatible validation while retaining the exact editable input.
        if (PhoneState(name = draft.name, code = draft.code, previewEnabled = true).canPreviewPairing) {
            onPair(draft.code, draft.name)
        }
    }

    fun requestPickup() {
        if (state.value.mode == PhoneUiMode.LIVE && liveState().canRequest) onRequestPickup()
    }

    fun endSession() {
        if (state.value.mode == PhoneUiMode.LIVE && liveState().isActive) onEndSession()
    }

    fun reconnect() {
        if (state.value.mode == PhoneUiMode.LIVE && canReconnectLivePhone(liveState())) onReconnect()
    }
}

fun livePhoneScreen(state: LivePhoneState): LivePhoneScreen = when (state.phase) {
    PhonePhase.UNPAIRED -> LivePhoneScreen.PAIR
    PhonePhase.IDLE -> LivePhoneScreen.HOME
    PhonePhase.REQUESTING -> LivePhoneScreen.REQUESTING
    PhonePhase.SHARING -> LivePhoneScreen.SHARING
    PhonePhase.ENDED -> LivePhoneScreen.ENDED
}

/** ETA meters are the car's reported route distance, never a derived GPS separation. */
fun liveSharingInfo(state: LivePhoneState): SharingInfo? = if (state.phase == PhonePhase.SHARING) {
    SharingInfo(
        distanceMeters = state.eta?.meters?.takeIf { it >= 0 },
        etaSeconds = state.eta?.seconds?.takeIf { it >= 0 },
        etaSource = state.eta?.source,
        lightOn = state.lightOn,
    )
} else null

fun canReconnectLivePhone(state: LivePhoneState): Boolean =
    state.pairing != null && !state.pairingInProgress &&
        state.connection in setOf(ConnectionState.DISCONNECTED, ConnectionState.REPLACED)
