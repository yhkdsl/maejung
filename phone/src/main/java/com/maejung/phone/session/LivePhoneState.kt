package com.maejung.phone.session

import com.maejung.phone.location.LocationFix
import com.maejung.phone.net.ConnectionState
import com.maejung.phone.store.StoredPairing
import com.maejung.protocol.PeerEta
import com.maejung.protocol.PeerLocation

enum class PhonePhase { UNPAIRED, IDLE, REQUESTING, SHARING, ENDED }

/** Actual application-owned session state, separate from the P2 preview model. */
data class LivePhoneState(
    val phase: PhonePhase = PhonePhase.UNPAIRED,
    val pairing: StoredPairing? = null,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val synchronized: Boolean = false,
    val pairingInProgress: Boolean = false,
    val sessionId: String? = null,
    val error: String? = null,
    val endReason: String? = null,
    val peerLocation: PeerLocation? = null,
    val eta: PeerEta? = null,
    val lightOn: Boolean? = null,
    val ownLocation: LocationFix? = null,
    val locationNotice: String? = null,
) {
    val canRequest: Boolean
        get() = phase == PhonePhase.IDLE && pairing != null &&
            connection == ConnectionState.CONNECTED && synchronized
    val isActive: Boolean
        get() = phase == PhonePhase.REQUESTING || phase == PhonePhase.SHARING
}
