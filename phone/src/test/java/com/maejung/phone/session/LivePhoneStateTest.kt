package com.maejung.phone.session

import com.maejung.phone.net.ConnectionState
import com.maejung.phone.store.StoredPairing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LivePhoneStateTest {
    @Test
    fun `live state starts without GPS or a location notice`() {
        val state = LivePhoneState()
        assertNull(state.ownLocation)
        assertNull(state.locationNotice)
    }

    @Test
    fun `pickup requires an idle paired connected and synchronized state`() {
        val pairing = StoredPairing("r_1", "v_1", "token_1", "민지")
        val ready = LivePhoneState(
            phase = PhonePhase.IDLE,
            pairing = pairing,
            connection = ConnectionState.CONNECTED,
            synchronized = true,
        )

        assertTrue(ready.canRequest)
        assertFalse(ready.copy(pairing = null).canRequest)
        assertFalse(ready.copy(synchronized = false).canRequest)
        for (connection in ConnectionState.entries.filter { it != ConnectionState.CONNECTED }) {
            assertFalse(ready.copy(connection = connection).canRequest, connection.name)
        }
        for (phase in PhonePhase.entries.filter { it != PhonePhase.IDLE }) {
            assertFalse(ready.copy(phase = phase).canRequest, phase.name)
        }
    }

    @Test
    fun `only requesting and sharing have an active session`() {
        val expected = mapOf(
            PhonePhase.UNPAIRED to false,
            PhonePhase.IDLE to false,
            PhonePhase.REQUESTING to true,
            PhonePhase.SHARING to true,
            PhonePhase.ENDED to false,
        )
        for ((phase, active) in expected) {
            assertEquals(active, LivePhoneState(phase = phase).isActive, phase.name)
        }
    }
}
