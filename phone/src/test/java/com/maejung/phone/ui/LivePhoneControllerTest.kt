package com.maejung.phone.ui

import com.maejung.phone.net.ConnectionState
import com.maejung.phone.location.LocationFix
import com.maejung.phone.session.LivePhoneState
import com.maejung.phone.session.PhonePhase
import com.maejung.phone.store.StoredPairing
import com.maejung.protocol.PeerEta
import com.maejung.protocol.PeerLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LivePhoneControllerTest {
    @Test
    fun `launch starts in live mode and projects the stored pairing as home`() {
        val fixture = Fixture(ready())
        assertEquals(PhoneUiMode.LIVE, fixture.controller.state.value.mode)
        assertEquals(LivePhoneScreen.HOME, livePhoneScreen(fixture.live))
    }

    @Test
    fun `release cannot enter preview but can send an actual pickup request`() {
        val fixture = Fixture(ready(), previewEnabled = false)
        assertFalse(fixture.controller.enterPreview())
        fixture.controller.requestPickup()
        assertEquals(listOf("request"), fixture.calls)
        assertEquals(PhoneUiMode.LIVE, fixture.controller.state.value.mode)
    }

    @Test
    fun `preview callbacks cannot pair request end or reconnect the live manager`() {
        val fixture = Fixture(ready())
        assertTrue(fixture.controller.enterPreview())
        fixture.controller.updateName("민지")
        fixture.controller.updateCode("012345")
        fixture.live = LivePhoneState()
        fixture.controller.pair()
        fixture.live = ready()
        fixture.controller.requestPickup()
        fixture.live = ready().copy(phase = PhonePhase.SHARING, sessionId = "s_live")
        fixture.controller.endSession()
        fixture.live = ready().copy(connection = ConnectionState.REPLACED)
        fixture.controller.reconnect()
        assertTrue(fixture.calls.isEmpty())
    }

    @Test
    fun `returning from preview exposes real actions without changing live session state`() {
        val fixture = Fixture(ready())
        val original = fixture.live
        assertTrue(fixture.controller.enterPreview())
        fixture.controller.returnToLive()
        fixture.controller.requestPickup()
        assertEquals(PhoneUiMode.LIVE, fixture.controller.state.value.mode)
        assertEquals(original, fixture.live)
        assertEquals(listOf("request"), fixture.calls)
    }

    @Test
    fun `an ongoing request or sharing session cannot be hidden by preview even offline`() {
        for (phase in listOf(PhonePhase.REQUESTING, PhonePhase.SHARING)) {
            val fixture = Fixture(ready().copy(phase = phase, connection = ConnectionState.DISCONNECTED))
            assertFalse(fixture.controller.enterPreview(), phase.name)
            assertEquals(PhoneUiMode.LIVE, fixture.controller.state.value.mode)
        }
    }

    @Test
    fun `a real session becoming active exits preview and does not restore it after ending`() {
        val fixture = Fixture(ready())
        assertTrue(fixture.controller.enterPreview())
        fixture.live = ready().copy(phase = PhonePhase.REQUESTING)
        fixture.controller.refreshLiveState()
        assertEquals(PhoneUiMode.LIVE, fixture.controller.state.value.mode)
        fixture.live = ready().copy(phase = PhonePhase.ENDED)
        fixture.controller.refreshLiveState()
        assertEquals(PhoneUiMode.LIVE, fixture.controller.state.value.mode)
        assertTrue(fixture.calls.isEmpty())
    }

    @Test
    fun `offline and unsynchronized requests are gated and never queued for reconnection`() {
        val fixture = Fixture(ready())
        for (connection in ConnectionState.entries.filter { it != ConnectionState.CONNECTED }) {
            fixture.live = ready().copy(connection = connection)
            fixture.controller.requestPickup()
        }
        fixture.live = ready().copy(synchronized = false)
        fixture.controller.requestPickup()
        fixture.live = ready()
        fixture.controller.refreshLiveState()
        assertTrue(fixture.calls.isEmpty())
        fixture.controller.requestPickup()
        assertEquals(listOf("request"), fixture.calls)
    }

    @Test
    fun `valid pairing preserves a leading zero and raw name for the manager`() {
        val fixture = Fixture(LivePhoneState())
        fixture.controller.updateName("  민지  ")
        fixture.controller.updateCode("012345")
        fixture.controller.pair()
        assertEquals(listOf("pair:012345:  민지  "), fixture.calls)
        assertEquals("  민지  ", fixture.controller.state.value.name)
        assertEquals("012345", fixture.controller.state.value.code)
    }

    @Test
    fun `invalid names and codes remain editable and never reach the pairing boundary`() {
        val inputs = listOf(
            "   " to "012345", "가".repeat(21) to "012345", "😀".repeat(11) to "012345",
            "민지" to "１２３４５６", "민지" to " 012345", "민지" to "12a", "민지" to "0123456",
        )
        for ((name, code) in inputs) {
            val fixture = Fixture(LivePhoneState())
            fixture.controller.updateName(name)
            fixture.controller.updateCode(code)
            fixture.controller.pair()
            assertTrue(fixture.calls.isEmpty())
            assertTrue(fixture.controller.state.value.validationShown)
            assertEquals(name, fixture.controller.state.value.name)
            assertEquals(code, fixture.controller.state.value.code)
        }
    }

    @Test
    fun `REST pairing errors preserve the submitted fields for correction and retry`() {
        val fixture = Fixture(LivePhoneState())
        fixture.controller.updateName("민지")
        fixture.controller.updateCode("000123")
        fixture.controller.pair()
        fixture.live = fixture.live.copy(error = "코드를 다시 확인해 주세요")
        fixture.controller.refreshLiveState()
        assertEquals("민지", fixture.controller.state.value.name)
        assertEquals("000123", fixture.controller.state.value.code)
        fixture.controller.pair()
        assertEquals(listOf("pair:000123:민지", "pair:000123:민지"), fixture.calls)
    }

    @Test
    fun `a pairing already in progress cannot be submitted twice`() {
        val fixture = Fixture(LivePhoneState(pairingInProgress = true))
        fixture.controller.updateName("민지")
        fixture.controller.updateCode("012345")
        fixture.controller.pair()
        assertTrue(fixture.calls.isEmpty())
    }

    @Test
    fun `4409 exposes explicit reconnect without reconnecting merely from state observation`() {
        val fixture = Fixture(ready().copy(connection = ConnectionState.REPLACED, synchronized = false))
        fixture.controller.refreshLiveState()
        assertTrue(canReconnectLivePhone(fixture.live))
        assertTrue(fixture.calls.isEmpty())
        fixture.controller.reconnect()
        assertEquals(listOf("reconnect"), fixture.calls)
    }

    @Test
    fun `an active offline session can still be ended but an idle one cannot`() {
        val fixture = Fixture(ready())
        fixture.controller.endSession()
        assertTrue(fixture.calls.isEmpty())
        fixture.live = ready().copy(phase = PhonePhase.SHARING, connection = ConnectionState.DISCONNECTED)
        fixture.controller.endSession()
        assertEquals(listOf("end"), fixture.calls)
    }

    @Test
    fun `P4 live sharing projects GPS separation and course while preserving received ETA and light`() {
        val live = ready().copy(
            phase = PhonePhase.SHARING, sessionId = "s_real",
            ownLocation = LocationFix(0.0, 0.0, 7f, 35f),
            peerLocation = PeerLocation(0.0, .001, 7f),
            eta = PeerEta(77, 890, "navi"), lightOn = true,
        )
        assertEquals(LivePhoneScreen.SHARING, livePhoneScreen(live))
        val telemetry = assertNotNull(liveSharingInfo(live))
        assertEquals(77, telemetry.etaSeconds)
        assertEquals(111, telemetry.distanceMeters)
        assertEquals("navi", telemetry.etaSource)
        assertEquals(true, telemetry.lightOn)
        assertEquals(90, telemetry.bearingDegrees)
        assertEquals(35, telemetry.courseDegrees)
    }

    @Test
    fun `missing live telemetry stays unknown instead of using the preview sample`() {
        val telemetry = assertNotNull(liveSharingInfo(ready().copy(phase = PhonePhase.SHARING,
            eta = PeerEta(90, 890, "navi"))))
        assertNull(telemetry.distanceMeters)
        assertEquals(90, telemetry.etaSeconds)
        assertNull(telemetry.lightOn)
        assertNull(telemetry.bearingDegrees)
        assertNull(telemetry.courseDegrees)
    }

    @Test
    fun `every live phase selects its real screen and hides stale sharing telemetry`() {
        val screens = listOf(
            PhonePhase.UNPAIRED to LivePhoneScreen.PAIR,
            PhonePhase.IDLE to LivePhoneScreen.HOME,
            PhonePhase.REQUESTING to LivePhoneScreen.REQUESTING,
            PhonePhase.ENDED to LivePhoneScreen.ENDED,
        )
        for ((phase, screen) in screens) {
            val live = ready().copy(
                phase = phase, peerLocation = PeerLocation(37.5, 127.1, 7f),
                eta = PeerEta(90, 120, "estimate"), lightOn = true, endReason = "manual",
            )
            assertEquals(screen, livePhoneScreen(live))
            assertNull(liveSharingInfo(live))
        }
    }

    private class Fixture(initial: LivePhoneState, previewEnabled: Boolean = true) {
        var live = initial
        val calls = mutableListOf<String>()
        val controller = LivePhoneController(
            liveState = { live }, previewEnabled = previewEnabled,
            onPair = { code, name -> calls += "pair:$code:$name" },
            onRequestPickup = { calls += "request" }, onEndSession = { calls += "end" },
            onReconnect = { calls += "reconnect" },
        )
    }

    private companion object {
        fun ready() = LivePhoneState(
            phase = PhonePhase.IDLE,
            pairing = StoredPairing("r_real", "v_real", "test-token", "민지"),
            connection = ConnectionState.CONNECTED, synchronized = true,
        )
    }
}
