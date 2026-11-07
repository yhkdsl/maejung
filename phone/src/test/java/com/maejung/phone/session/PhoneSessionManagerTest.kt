package com.maejung.phone.session

import com.maejung.phone.location.LocationFix
import com.maejung.phone.net.ApiException
import com.maejung.phone.net.ConnectionState
import com.maejung.phone.net.PairingApi
import com.maejung.phone.net.SessionTransport
import com.maejung.phone.store.PairingStore
import com.maejung.phone.store.StoredPairing
import com.maejung.protocol.ClientMessage
import com.maejung.protocol.ErrorMessage
import com.maejung.protocol.LocationUpdate
import com.maejung.protocol.Message
import com.maejung.protocol.PairingResult
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
import com.maejung.protocol.Unknown
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneSessionManagerTest {
    @Test
    fun `restored pairing connects but cannot request before the first snapshot`() = runTest {
        val f = fixture()
        runCurrent()

        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertEquals(PAIRING, f.state.pairing)
        assertEquals(listOf("token_1"), f.transport.tokens)
        assertFalse(f.state.synchronized)
        f.manager.requestPickup()
        runCurrent()
        assertTrue(f.transport.sent.isEmpty())
    }

    @Test
    fun `incoming collector is subscribed before connect emits its immediate snapshot`() = runTest {
        val f = fixture(snapshotOnConnect = Received(SessionState("sharing", "r_1", "민지"), "s_1", 1))
        runCurrent()

        assertEquals(PhonePhase.SHARING, f.state.phase)
        assertEquals("s_1", f.state.sessionId)
        assertTrue(f.state.synchronized)
    }

    @Test
    fun `pair saves the normalized name and connects exactly once despite repeated taps`() = runTest {
        val f = fixture(pairing = null)
        val response = CompletableDeferred<PairingResult>()
        f.api.response = { response.await() }
        f.manager.pair("012345", "  민지  ")
        f.manager.pair("012345", "  민지  ")
        runCurrent()

        assertTrue(f.state.pairingInProgress)
        assertEquals(listOf("012345" to "민지"), f.api.requests)
        response.complete(PairingResult("r_1", "v_1", "token_1"))
        runCurrent()
        assertEquals(PAIRING, f.store.saved)
        assertEquals(PAIRING, f.state.pairing)
        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertFalse(f.state.pairingInProgress)
        assertFalse(f.state.synchronized)
        assertEquals(listOf("token_1"), f.transport.tokens)
    }

    @Test
    fun `pairing HTTP 400 and 404 give the Korean code prompt without credentials`() = runTest {
        for ((status, code) in listOf(400 to "INVALID_RIDER_NAME", 404 to "PAIRING_CODE_NOT_FOUND")) {
            val f = fixture(pairing = null)
            f.api.response = { throw ApiException(status, code, "server detail") }
            f.manager.pair("012345", "민지")
            runCurrent()

            assertEquals(PhonePhase.UNPAIRED, f.state.phase)
            assertEquals("코드를 다시 확인해 주세요", f.state.error)
            assertFalse(f.state.pairingInProgress)
            assertNull(f.store.saved)
            assertTrue(f.transport.tokens.isEmpty())
            f.manager.close()
        }
    }

    @Test
    fun `pairing network failure remains unpaired with a connection prompt`() = runTest {
        val f = fixture(pairing = null)
        f.api.response = { throw IOException("offline") }
        f.manager.pair("012345", "민지")
        runCurrent()

        assertEquals(PhonePhase.UNPAIRED, f.state.phase)
        assertEquals("연결을 확인해 주세요", f.state.error)
        assertFalse(f.state.pairingInProgress)
        assertNull(f.store.saved)
    }

    @Test
    fun `request sends one rider name then times out at ten seconds without acknowledgement`() = runTest {
        val f = fixture()
        ready(f)
        repeat(4) { f.manager.requestPickup() }
        runCurrent()

        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertNull(f.state.sessionId)
        assertEquals(listOf<ClientMessage>(PickupRequest("민지")), f.transport.sent)
        advanceTimeBy(9_999)
        runCurrent()
        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertEquals("연결을 확인해 주세요", f.state.error)
    }

    @Test
    fun `an unacknowledged pickup expires at its original deadline while disconnected`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        f.transport.connection.value = ConnectionState.DISCONNECTED
        runCurrent()
        advanceTimeBy(8_999)
        runCurrent()

        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertNull(f.state.sessionId)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertEquals("연결을 확인해 주세요", f.state.error)
        assertFalse(f.state.synchronized)
        assertFalse(f.state.canRequest)
    }

    @Test
    fun `reconnect attempts cannot cancel or restart an unacknowledged pickup deadline`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        f.transport.connection.value = ConnectionState.DISCONNECTED
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        f.manager.reconnect()
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        f.transport.connection.value = ConnectionState.DISCONNECTED
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        f.manager.reconnect()
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()

        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertNull(f.state.sessionId)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertEquals("연결을 확인해 주세요", f.state.error)
        assertFalse(f.state.synchronized)
        assertEquals(listOf<ClientMessage>(PickupRequest("민지")), f.transport.sent)
    }

    @Test
    fun `bound requested and sharing sessions survive disconnects beyond the acknowledgement deadline`() = runTest {
        for (accepted in listOf(false, true)) {
            val f = fixture()
            ready(f)
            f.manager.requestPickup()
            runCurrent()
            f.transport.receive(PickupPending, "s_1")
            runCurrent()
            if (accepted) {
                f.transport.receive(PickupAccepted, "s_1")
                runCurrent()
            }
            f.transport.connection.value = ConnectionState.DISCONNECTED
            runCurrent()
            f.manager.reconnect()
            runCurrent()
            advanceTimeBy(20_000)
            runCurrent()

            assertEquals(if (accepted) PhonePhase.SHARING else PhonePhase.REQUESTING, f.state.phase)
            assertEquals("s_1", f.state.sessionId)
            assertFalse(f.state.synchronized)
            f.manager.close()
        }
    }

    @Test
    fun `pickup timeout reconnects and gates late acknowledgements until a requested snapshot`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertEquals("연결을 확인해 주세요", f.state.error)
        assertFalse(f.state.synchronized)
        assertFalse(f.state.canRequest)
        assertEquals(listOf("token_1", "token_1"), f.transport.tokens)
        f.transport.connection.value = ConnectionState.CONNECTED
        runCurrent()
        f.transport.receive(PickupPending, "s_late")
        f.transport.receive(PickupAccepted, "s_late")
        runCurrent()
        f.manager.requestPickup()
        runCurrent()
        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertNull(f.state.sessionId)
        assertEquals("연결을 확인해 주세요", f.state.error)
        assertFalse(f.state.synchronized)
        assertEquals(listOf<ClientMessage>(PickupRequest("민지")), f.transport.sent)

        f.transport.receive(SessionState("requested", "r_1", "민지"), "s_restored")
        runCurrent()
        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertEquals("s_restored", f.state.sessionId)
        assertTrue(f.state.synchronized)
        assertNull(f.state.error)
        assertFalse(f.state.canRequest)
    }

    @Test
    fun `a sharing snapshot recovers a timed out pickup despite delayed acknowledgements`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertFalse(f.state.canRequest)
        assertFalse(f.state.synchronized)
        assertEquals(listOf("token_1", "token_1"), f.transport.tokens)
        f.transport.connection.value = ConnectionState.CONNECTED
        runCurrent()
        f.transport.receive(PickupPending, "s_active")
        f.transport.receive(PickupAccepted, "s_active")
        runCurrent()
        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertFalse(f.state.synchronized)
        f.transport.receive(SessionState("sharing", "r_1", "민지"), "s_active")
        runCurrent()

        assertEquals(PhonePhase.SHARING, f.state.phase)
        assertEquals("s_active", f.state.sessionId)
        assertTrue(f.state.synchronized)
        assertNull(f.state.error)
        assertEquals(listOf<ClientMessage>(PickupRequest("민지")), f.transport.sent)
    }

    @Test
    fun `a none snapshot unlocks a fresh pickup after timeout reconciliation`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertFalse(f.state.canRequest)
        assertEquals("연결을 확인해 주세요", f.state.error)
        ready(f)
        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertTrue(f.state.synchronized)
        assertTrue(f.state.canRequest)
        assertNull(f.state.error)
        f.manager.requestPickup()
        runCurrent()
        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertEquals(listOf<ClientMessage>(PickupRequest("민지"), PickupRequest("민지")), f.transport.sent)
    }

    @Test
    fun `a replaced connection cannot be automatically reclaimed by pickup timeout recovery`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        f.transport.connection.value = ConnectionState.REPLACED
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertNull(f.state.sessionId)
        assertEquals("연결을 확인해 주세요", f.state.error)
        assertEquals(ConnectionState.REPLACED, f.state.connection)
        assertEquals(PAIRING, f.state.pairing)
        assertEquals(PAIRING, f.store.saved)
        assertFalse(f.state.canRequest)
        assertEquals(listOf("token_1"), f.transport.tokens)
        f.manager.reconnect()
        runCurrent()
        assertEquals(listOf("token_1", "token_1"), f.transport.tokens)
    }

    @Test
    fun `timeout cannot reconnect an unauthorized socket before its connection notification is collected`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()
        // The socket's live value is terminal, but its collector has not handled that change yet.
        f.transport.connection.deferNotification(ConnectionState.UNAUTHORIZED)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("token_1"), f.transport.tokens)
        assertFalse(f.state.canRequest)
        f.transport.connection.publishLatest()
        runCurrent()
        assertEquals(PhonePhase.UNPAIRED, f.state.phase)
        assertNull(f.state.pairing)
        assertNull(f.store.saved)
        assertTrue(f.transport.closeCalls > 0)
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(listOf("token_1"), f.transport.tokens)
    }

    @Test
    fun `pending binds the session and cancels the local acknowledgement timer`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        f.transport.receive(PickupPending, "s_1")
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertEquals("s_1", f.state.sessionId)
        assertNull(f.state.error)
        f.transport.receive(PickupAccepted, "s_1")
        runCurrent()
        assertEquals(PhonePhase.SHARING, f.state.phase)
    }

    @Test
    fun `manual end sends the protocol command and returns to idle after three seconds`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.endSession()
        f.manager.endSession()
        runCurrent()

        assertEquals(listOf<ClientMessage>(SessionEnd("manual")), f.transport.sent)
        assertEquals(PhonePhase.ENDED, f.state.phase)
        assertEquals("manual", f.state.endReason)
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(PhonePhase.ENDED, f.state.phase)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertNull(f.state.endReason)
        assertNull(f.state.sessionId)
        assertTrue(f.state.canRequest)
    }

    @Test
    fun `offline end stays active and honestly asks for a retry after reconnect`() = runTest {
        val f = fixture()
        sharing(f)
        f.transport.connection.value = ConnectionState.DISCONNECTED
        runCurrent()
        f.manager.endSession()
        runCurrent()

        assertEquals(PhonePhase.SHARING, f.state.phase)
        assertEquals("s_1", f.state.sessionId)
        assertEquals("종료 요청을 보내지 못했어요. 연결 후 다시 시도해 주세요", f.state.error)
        assertTrue(f.transport.sent.isEmpty())
        f.manager.reconnect()
        runCurrent()
        sharing(f)
        f.manager.endSession()
        runCurrent()
        assertEquals(listOf<ClientMessage>(SessionEnd("manual")), f.transport.sent)
        assertEquals(PhonePhase.ENDED, f.state.phase)
    }

    @Test
    fun `unauthorized clears credentials and cannot reconnect with the rejected token`() = runTest {
        val f = fixture()
        sharing(f)
        f.transport.connection.value = ConnectionState.UNAUTHORIZED
        runCurrent()

        assertEquals(PhonePhase.UNPAIRED, f.state.phase)
        assertNull(f.state.pairing)
        assertNull(f.store.saved)
        assertFalse(f.state.synchronized)
        assertNull(f.state.sessionId)
        f.manager.reconnect()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf("token_1"), f.transport.tokens)
        assertTrue(f.transport.closeCalls > 0)
    }

    @Test
    fun `replaced retains credentials and session without retry until explicit reconnect`() = runTest {
        val f = fixture()
        sharing(f)
        f.transport.connection.value = ConnectionState.REPLACED
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(PAIRING, f.store.saved)
        assertEquals("s_1", f.state.sessionId)
        assertEquals(ConnectionState.REPLACED, f.state.connection)
        assertFalse(f.state.synchronized)
        assertFalse(f.state.canRequest)
        assertEquals(listOf("token_1"), f.transport.tokens)
        f.manager.reconnect()
        runCurrent()
        assertEquals(listOf("token_1", "token_1"), f.transport.tokens)
        assertFalse(f.state.synchronized)
        ready(f)
        assertEquals(PhonePhase.ENDED, f.state.phase)
        assertEquals("peer_lost", f.state.endReason)
    }

    @Test
    fun `close stops owned collectors and timers while keeping the supplied scope alive`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.endSession()
        runCurrent()
        f.manager.close()
        val closedState = f.state
        f.transport.receive(SessionState("sharing", "r_1", "민지"), "s_2")
        f.transport.connection.value = ConnectionState.UNAUTHORIZED
        var scopeAlive = false
        backgroundScope.launch { scopeAlive = true }
        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(closedState, f.state)
        assertTrue(scopeAlive)
        assertTrue(f.transport.closeCalls > 0)
    }

    @Test
    fun `connected without a synchronization snapshot cannot issue a pickup`() = runTest {
        val f = fixture()
        runCurrent()
        f.transport.connection.value = ConnectionState.CONNECTED
        runCurrent()
        f.manager.requestPickup()
        runCurrent()

        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertFalse(f.state.synchronized)
        assertFalse(f.state.canRequest)
        assertTrue(f.transport.sent.isEmpty())
        ready(f)
        assertTrue(f.state.canRequest)
    }

    @Test
    fun `a failed pickup send stays idle with a connection prompt`() = runTest {
        val f = fixture()
        ready(f)
        f.transport.sendSucceeds = false
        f.manager.requestPickup()
        runCurrent()

        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertEquals("연결을 확인해 주세요", f.state.error)
        assertNull(f.state.sessionId)
    }

    @Test
    fun `a failed manual end send keeps the real session active`() = runTest {
        val f = fixture()
        sharing(f)
        f.transport.sendSucceeds = false
        f.manager.endSession()
        runCurrent()

        assertEquals(PhonePhase.SHARING, f.state.phase)
        assertEquals("s_1", f.state.sessionId)
        assertEquals("종료 요청을 보내지 못했어요. 연결 후 다시 시도해 주세요", f.state.error)
        assertNull(f.state.endReason)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(PhonePhase.SHARING, f.state.phase)
    }

    @Test
    fun `request errors return idle with the corresponding Korean explanation`() = runTest {
        for ((code, prompt) in listOf(
            "VEHICLE_OFFLINE" to "차가 꺼져 있어요",
            "SESSION_ALREADY_ACTIVE" to "이미 다른 요청이 진행 중이에요",
        )) {
            val f = fixture()
            ready(f)
            f.manager.requestPickup()
            runCurrent()
            f.transport.receive(ErrorMessage(code, "server detail"), null)
            runCurrent()

            assertEquals(PhonePhase.IDLE, f.state.phase)
            assertEquals(prompt, f.state.error)
            assertNull(f.state.sessionId)
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(prompt, f.state.error)
            f.manager.close()
        }
    }

    @Test
    fun `requested snapshot restores the session without starting a local acknowledgement timeout`() = runTest {
        val f = fixture()
        runCurrent()
        f.transport.connection.value = ConnectionState.CONNECTED
        runCurrent()
        f.transport.receive(SessionState("requested", "r_1", "민지"), "s_restored")
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertEquals("s_restored", f.state.sessionId)
        assertTrue(f.state.synchronized)
        assertFalse(f.state.canRequest)
        assertTrue(f.transport.sent.isEmpty())
    }

    @Test
    fun `snapshots with missing or blank active ids never unlock pickup`() = runTest {
        for (state in listOf("requested", "sharing")) {
            for (id in listOf(null, "", "  ")) {
                val f = fixture()
                runCurrent()
                f.transport.connection.value = ConnectionState.CONNECTED
                runCurrent()
                f.transport.receive(SessionState(state, "r_1", "민지"), id)
                runCurrent()

                assertEquals(PhonePhase.IDLE, f.state.phase)
                assertFalse(f.state.synchronized)
                assertNull(f.state.sessionId)
                assertFalse(f.state.canRequest)
                f.manager.close()
            }
        }
    }

    @Test
    fun `unknown snapshots and none with a session id are ignored without synchronizing`() = runTest {
        val f = fixture()
        runCurrent()
        f.transport.connection.value = ConnectionState.CONNECTED
        runCurrent()
        f.transport.receive(SessionState("future_state"), "s_future")
        f.transport.receive(SessionState("none"), "s_invalid")
        runCurrent()

        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertFalse(f.state.synchronized)
        assertNull(f.state.sessionId)
    }

    @Test
    fun `none snapshot ends both pending and sharing sessions as peer lost`() = runTest {
        for (activeState in listOf("requested", "sharing")) {
            val f = fixture()
            runCurrent()
            f.transport.connection.value = ConnectionState.CONNECTED
            runCurrent()
            f.transport.receive(SessionState(activeState, "r_1", "민지"), "s_1")
            runCurrent()
            assertTrue(f.state.isActive)
            f.transport.receive(SessionState("none"), null)
            runCurrent()

            assertEquals(PhonePhase.ENDED, f.state.phase)
            assertEquals("peer_lost", f.state.endReason)
            assertTrue(f.state.synchronized)
            f.manager.close()
        }
    }

    @Test
    fun `telemetry only updates a sharing session with its exact session id`() = runTest {
        val f = fixture()
        sharing(f)
        val location = PeerLocation(37.4983, 127.0628, 4.5f)
        val eta = PeerEta(90, 300, "navi")
        f.transport.receive(location, "s_1")
        f.transport.receive(eta, "s_1")
        f.transport.receive(PeerLight(true), "s_1")
        runCurrent()

        assertEquals(location, f.state.peerLocation)
        assertEquals(eta, f.state.eta)
        assertEquals(true, f.state.lightOn)
        val valid = f.state
        for (id in listOf(null, "", "s_other")) {
            f.transport.receive(PeerLocation(0.0, 0.0, 0f), id)
            f.transport.receive(PeerEta(0, 0, "estimate"), id)
            f.transport.receive(PeerLight(false), id)
            f.transport.receive(SessionEnded("manual"), id)
        }
        runCurrent()
        assertEquals(valid, f.state)
        assertTrue(f.transport.sent.isEmpty())
    }

    @Test
    fun `a new session snapshot clears all previous telemetry`() = runTest {
        val f = fixture()
        sharing(f)
        f.transport.receive(PeerLocation(37.4983, 127.0628, 4.5f), "s_1")
        f.transport.receive(PeerEta(90, 300, "estimate"), "s_1")
        f.transport.receive(PeerLight(true), "s_1")
        runCurrent()
        assertNotNull(f.state.peerLocation)
        assertNotNull(f.state.eta)
        assertEquals(true, f.state.lightOn)
        sharing(f, "s_2")

        assertEquals("s_2", f.state.sessionId)
        assertNull(f.state.peerLocation)
        assertNull(f.state.eta)
        assertNull(f.state.lightOn)
        f.transport.receive(SessionEnded("manual"), "s_1")
        runCurrent()
        assertEquals(PhonePhase.SHARING, f.state.phase)
    }

    @Test
    fun `pending and accepted need valid matching session ids`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        for (id in listOf(null, "", "  ")) {
            f.transport.receive(PickupPending, id)
            f.transport.receive(PickupAccepted, id)
        }
        runCurrent()
        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertNull(f.state.sessionId)
        f.transport.receive(PickupPending, "s_1")
        runCurrent()
        f.transport.receive(PickupPending, "s_other")
        f.transport.receive(PickupAccepted, "s_other")
        runCurrent()
        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertEquals("s_1", f.state.sessionId)
        f.transport.receive(PickupAccepted, "s_1")
        runCurrent()
        assertEquals(PhonePhase.SHARING, f.state.phase)
    }

    @Test
    fun `telemetry is ignored while idle or waiting for acceptance`() = runTest {
        val f = fixture()
        ready(f)
        for (request in listOf(false, true)) {
            if (request) {
                f.manager.requestPickup()
                runCurrent()
                f.transport.receive(PickupPending, "s_1")
                runCurrent()
                assertEquals(PhonePhase.REQUESTING, f.state.phase)
            } else {
                assertEquals(PhonePhase.IDLE, f.state.phase)
            }
            f.transport.receive(PeerLocation(37.4983, 127.0628, 4.5f), "s_1")
            f.transport.receive(PeerEta(90, 300, "navi"), "s_1")
            f.transport.receive(PeerLight(true), "s_1")
            runCurrent()
            assertNull(f.state.peerLocation)
            assertNull(f.state.eta)
            assertNull(f.state.lightOn)
        }
    }

    @Test
    fun `invalid numeric telemetry preserves the last valid values`() = runTest {
        val f = fixture()
        sharing(f)
        val goodLocation = PeerLocation(37.4983, 127.0628, 4.5f)
        val goodEta = PeerEta(90, 300, "navi")
        f.transport.receive(goodLocation, "s_1")
        f.transport.receive(goodEta, "s_1")
        runCurrent()
        for (invalid in listOf(
            PeerLocation(Double.NaN, 127.0, 4f),
            PeerLocation(37.0, Double.NaN, 4f),
            PeerLocation(Double.POSITIVE_INFINITY, 127.0, 4f),
            PeerLocation(37.0, Double.NEGATIVE_INFINITY, 4f),
            PeerLocation(90.001, 127.0, 4f),
            PeerLocation(-90.001, 127.0, 4f),
            PeerLocation(37.0, 180.001, 4f),
            PeerLocation(37.0, -180.001, 4f),
            PeerLocation(37.0, 127.0, Float.NaN),
            PeerLocation(37.0, 127.0, Float.POSITIVE_INFINITY),
            PeerLocation(37.0, 127.0, -1f),
        )) {
            f.transport.receive(invalid, "s_1")
            runCurrent()
            assertEquals(goodLocation, f.state.peerLocation)
        }
        for (invalid in listOf(
            PeerEta(-1, 300, "navi"),
            PeerEta(90, -1, "estimate"),
            PeerEta(90, 300, "future_source"),
        )) {
            f.transport.receive(invalid, "s_1")
            runCurrent()
            assertEquals(goodEta, f.state.eta)
        }
        f.transport.receive(PeerLocation(-90.0, 180.0, 0f), "s_1")
        f.transport.receive(PeerEta(0, 0, "estimate"), "s_1")
        runCurrent()
        assertEquals(PeerLocation(-90.0, 180.0, 0f), f.state.peerLocation)
        assertEquals(PeerEta(0, 0, "estimate"), f.state.eta)
    }

    @Test
    fun `all six server end reasons clear telemetry and return idle after three seconds`() = runTest {
        for (reason in listOf("boarded", "rejected", "manual", "request_timeout", "peer_lost", "max_duration")) {
            val f = fixture()
            sharing(f)
            f.transport.receive(PeerLocation(37.4983, 127.0628, 4.5f), "s_1")
            f.transport.receive(PeerEta(90, 300, "navi"), "s_1")
            f.transport.receive(PeerLight(true), "s_1")
            runCurrent()
            assertNotNull(f.state.peerLocation)
            f.transport.receive(SessionEnded(reason), "s_1")
            runCurrent()

            assertEquals(PhonePhase.ENDED, f.state.phase)
            assertEquals(reason, f.state.endReason)
            assertNull(f.state.peerLocation)
            assertNull(f.state.eta)
            assertNull(f.state.lightOn)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(PhonePhase.IDLE, f.state.phase)
            assertNull(f.state.endReason)
            f.manager.close()
        }
    }

    @Test
    fun `request cancellation can end before pending and late acknowledgements cannot resurrect it`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        f.manager.endSession()
        runCurrent()

        assertEquals(PhonePhase.ENDED, f.state.phase)
        assertEquals("manual", f.state.endReason)
        assertEquals(listOf<ClientMessage>(PickupRequest("민지"), SessionEnd("manual")), f.transport.sent)
        f.transport.receive(PickupPending, "s_late")
        f.transport.receive(PickupAccepted, "s_late")
        runCurrent()
        assertEquals(PhonePhase.ENDED, f.state.phase)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertNull(f.state.error)
    }

    @Test
    fun `a restored session cancels an older ended timer`() = runTest {
        val f = fixture()
        sharing(f)
        f.transport.receive(SessionEnded("manual"), "s_1")
        runCurrent()
        assertEquals(PhonePhase.ENDED, f.state.phase)
        advanceTimeBy(2_000)
        runCurrent()
        sharing(f, "s_2")
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(PhonePhase.SHARING, f.state.phase)
        assertEquals("s_2", f.state.sessionId)
        assertNull(f.state.endReason)
    }

    @Test
    fun `a disconnected idle session needs a fresh snapshot after reconnect`() = runTest {
        val f = fixture()
        ready(f)
        assertTrue(f.state.canRequest)
        f.transport.connection.value = ConnectionState.DISCONNECTED
        runCurrent()
        assertFalse(f.state.synchronized)
        f.transport.connection.value = ConnectionState.CONNECTED
        runCurrent()
        f.manager.requestPickup()
        runCurrent()

        assertEquals(PhonePhase.IDLE, f.state.phase)
        assertFalse(f.state.canRequest)
        assertTrue(f.transport.sent.isEmpty())
        ready(f)
        assertTrue(f.state.canRequest)
    }

    @Test
    fun `unknown frames and log-only server errors do not alter an active session`() = runTest {
        val f = fixture()
        sharing(f)
        val before = f.state
        f.transport.receive(Unknown("future.event", "{}"), "s_1")
        for (code in listOf("SESSION_NOT_FOUND", "INVALID_STATE", "BAD_MESSAGE", "NEW_ERROR")) {
            f.transport.receive(ErrorMessage(code, "server detail"), null)
        }
        f.transport.receive(PickupPending, "s_other")
        f.transport.receive(PickupAccepted, "s_other")
        runCurrent()

        assertEquals(before, f.state)
        assertTrue(f.transport.sent.isEmpty())
    }

    @Test
    fun `closing during pairing cannot persist a late response`() = runTest {
        val f = fixture(pairing = null)
        val response = CompletableDeferred<PairingResult>()
        f.api.response = { response.await() }
        f.manager.pair("012345", "민지")
        runCurrent()
        assertTrue(f.state.pairingInProgress)
        f.manager.close()
        response.complete(PairingResult("r_1", "v_1", "token_1"))
        runCurrent()

        assertNull(f.store.saved)
        assertNull(f.state.pairing)
        assertTrue(f.transport.tokens.isEmpty())
    }

    @Test
    fun `invalid local pairing input never consumes a one-use code`() = runTest {
        for ((code, name) in listOf(
            "12345" to "민지",
            "１２３４５６" to "민지",
            "012345" to "  ",
            "012345" to "가".repeat(21),
            "012345" to "😀".repeat(11),
        )) {
            val f = fixture(pairing = null)
            f.manager.pair(code, name)
            runCurrent()

            assertEquals(PhonePhase.UNPAIRED, f.state.phase)
            assertFalse(f.state.pairingInProgress)
            assertNotNull(f.state.error)
            assertTrue(f.api.requests.isEmpty())
            f.manager.close()
        }
    }

    @Test
    fun `an older request timeout cannot reset a newer request`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        f.transport.receive(ErrorMessage("VEHICLE_OFFLINE", "offline"), null)
        runCurrent()
        f.manager.requestPickup()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertNull(f.state.error)
        f.transport.receive(PickupPending, "s_second")
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(PhonePhase.REQUESTING, f.state.phase)
        assertEquals("s_second", f.state.sessionId)
    }

    @Test
    fun `an authoritative sharing snapshot cancels an unacknowledged request timer`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        advanceTimeBy(9_000)
        runCurrent()
        sharing(f, "s_recovered")
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(PhonePhase.SHARING, f.state.phase)
        assertEquals("s_recovered", f.state.sessionId)
        assertNull(f.state.error)
    }

    @Test
    fun `sharing without a GPS fix never sends a location heartbeat`() = runTest {
        val f = fixture()
        sharing(f)
        advanceTimeBy(123_000)
        runCurrent()
        assertNull(f.state.ownLocation)
        assertTrue(f.transport.sent.isEmpty())
    }

    @Test
    fun `GPS fixes before sharing are neither cached nor sent after acceptance`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.updateOwnLocation(FIX)
        f.manager.requestPickup()
        runCurrent()
        f.transport.receive(PickupPending, "s_1")
        runCurrent()
        f.manager.updateOwnLocation(FIX)
        f.transport.receive(PickupAccepted, "s_1")
        runCurrent()
        advanceTimeBy(6_000)
        runCurrent()
        assertNull(f.state.ownLocation)
        assertEquals(listOf<ClientMessage>(PickupRequest("민지")), f.transport.sent)
    }

    @Test
    fun `first fix sends immediately and latest fix repeats at the original three second boundary`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        assertEquals(FIX, f.state.ownLocation)
        assertEquals(listOf<ClientMessage>(LocationUpdate(37.4983, 127.0628, 4f)), f.transport.sent)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        val latest = FIX.copy(lat = 37.4984, courseDegrees = 90f)
        f.manager.updateOwnLocation(latest)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, f.transport.sent.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(latest, f.state.ownLocation)
        assertEquals(LocationUpdate(37.4984, 127.0628, 4f), f.transport.sent.last())
        assertEquals(2, f.transport.sent.size)
    }

    @Test
    fun `stationary latest fix keeps heartbeating beyond two minutes`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        advanceTimeBy(123_000)
        runCurrent()
        assertEquals(42, f.transport.sent.size)
        assertTrue(f.transport.sent.all { it == LocationUpdate(37.4983, 127.0628, 4f) })
    }

    @Test
    fun `invalid GPS coordinates accuracy and course preserve the last valid fix`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        for (invalid in listOf(
            FIX.copy(lat = Double.NaN), FIX.copy(lng = Double.NaN),
            FIX.copy(lat = Double.POSITIVE_INFINITY), FIX.copy(lng = Double.NEGATIVE_INFINITY),
            FIX.copy(lat = 90.001), FIX.copy(lat = -90.001),
            FIX.copy(lng = 180.001), FIX.copy(lng = -180.001),
            FIX.copy(accuracy = Float.NaN), FIX.copy(accuracy = Float.POSITIVE_INFINITY),
            FIX.copy(accuracy = -1f), FIX.copy(courseDegrees = Float.NaN),
            FIX.copy(courseDegrees = Float.POSITIVE_INFINITY), FIX.copy(courseDegrees = Float.NEGATIVE_INFINITY),
        )) {
            f.manager.updateOwnLocation(invalid)
            assertEquals(FIX, f.state.ownLocation)
        }
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf<ClientMessage>(
            LocationUpdate(37.4983, 127.0628, 4f), LocationUpdate(37.4983, 127.0628, 4f),
        ), f.transport.sent)
        val boundary = LocationFix(-90.0, 180.0, 0f, courseDegrees = 0f)
        f.manager.updateOwnLocation(boundary)
        assertEquals(boundary, f.state.ownLocation)
    }

    @Test
    fun `ordinary reconnect retains the latest fix but waits for the same session snapshot`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        f.transport.connection.value = ConnectionState.DISCONNECTED
        runCurrent()
        f.manager.reconnect()
        runCurrent()
        f.transport.connection.value = ConnectionState.CONNECTED
        runCurrent()
        advanceTimeBy(9_000)
        runCurrent()
        assertEquals(FIX, f.state.ownLocation)
        assertEquals(1, f.transport.sent.size)
        sharing(f)
        advanceTimeBy(3_000)
        runCurrent()
        assertTrue(f.transport.sent.size >= 2)
        assertTrue(f.transport.sent.all { it == LocationUpdate(37.4983, 127.0628, 4f) })
    }

    @Test
    fun `live terminal transport gate prevents sends before its notification is collected`() = runTest {
        for (terminal in listOf(ConnectionState.REPLACED, ConnectionState.UNAUTHORIZED)) {
            val f = fixture()
            sharing(f)
            f.manager.updateOwnLocation(FIX)
            runCurrent()
            advanceTimeBy(2_999)
            runCurrent()
            f.transport.connection.deferNotification(terminal)
            f.manager.updateOwnLocation(FIX.copy(lat = 37.4984))
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(1, f.transport.sent.size, terminal.name)
            assertEquals(listOf("token_1"), f.transport.tokens)
            f.manager.close()
        }
    }

    @Test
    fun `new session snapshot discards old fix and cancels its heartbeat`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        sharing(f, "s_2")
        assertNull(f.state.ownLocation)
        advanceTimeBy(9_000)
        runCurrent()
        assertEquals(1, f.transport.sent.size)
        f.manager.updateOwnLocation(FIX.copy(lat = 37.4984))
        assertEquals(2, f.transport.sent.size)
    }

    @Test
    fun `requested snapshot clears the previous sharing fix`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        f.transport.receive(SessionState("requested", "r_1", "민지"), "s_1")
        runCurrent()
        assertNull(f.state.ownLocation)
        advanceTimeBy(9_000)
        runCurrent()
        assertEquals(1, f.transport.sent.size)
    }

    @Test
    fun `clearing own location stops heartbeats and a later fix sends immediately`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        f.manager.clearOwnLocation()
        assertNull(f.state.ownLocation)
        advanceTimeBy(9_000)
        runCurrent()
        assertEquals(1, f.transport.sent.size)
        f.manager.updateOwnLocation(FIX)
        assertEquals(2, f.transport.sent.size)
    }

    @Test
    fun `location notice is separate from session errors and can be cleared`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.setLocationNotice("위치 권한을 허용해 주세요")
        assertEquals("위치 권한을 허용해 주세요", f.state.locationNotice)
        assertNull(f.state.error)
        assertTrue(f.state.canRequest)
        f.manager.setLocationNotice(null)
        assertNull(f.state.locationNotice)
    }

    @Test
    fun `manual end clears own location and cancels heartbeats`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        f.manager.endSession()
        assertNull(f.state.ownLocation)
        assertEquals(SessionEnd("manual"), f.transport.sent.last())
        advanceTimeBy(12_000)
        runCurrent()
        assertEquals(2, f.transport.sent.size)
        f.manager.updateOwnLocation(FIX)
        assertNull(f.state.ownLocation)
    }

    @Test
    fun `server end clears own location for every supported reason`() = runTest {
        for (reason in listOf("boarded", "rejected", "manual", "request_timeout", "peer_lost", "max_duration")) {
            val f = fixture()
            sharing(f)
            f.manager.updateOwnLocation(FIX)
            runCurrent()
            f.transport.receive(SessionEnded(reason), "s_1")
            runCurrent()
            assertNull(f.state.ownLocation, reason)
            advanceTimeBy(6_000)
            runCurrent()
            assertEquals(1, f.transport.sent.size, reason)
            f.manager.close()
        }
    }

    @Test
    fun `unauthorized connection drops cached GPS along with credentials`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        f.transport.connection.value = ConnectionState.UNAUTHORIZED
        runCurrent()
        assertNull(f.state.ownLocation)
        f.manager.updateOwnLocation(FIX)
        advanceTimeBy(9_000)
        runCurrent()
        assertNull(f.state.ownLocation)
        assertEquals(1, f.transport.sent.size)
    }

    @Test
    fun `app closed sends app_closed once then stops location and socket retries`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        f.manager.appClosed()
        f.manager.appClosed()
        assertEquals(SessionEnd("app_closed"), f.transport.sent.last())
        assertEquals(2, f.transport.sent.size)
        assertNull(f.state.ownLocation)
        assertFalse(f.state.isActive)
        assertFalse(f.state.synchronized)
        assertEquals(1, f.transport.closeCalls)
        f.manager.updateOwnLocation(FIX)
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(2, f.transport.sent.size)
        assertEquals(listOf("token_1"), f.transport.tokens)
    }

    @Test
    fun `offline app closed never queues an end and explicit reconnect can reopen the manager`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        f.transport.connection.value = ConnectionState.DISCONNECTED
        runCurrent()
        f.manager.appClosed()
        assertNull(f.state.ownLocation)
        assertTrue(f.transport.closeCalls > 0)
        f.transport.connection.value = ConnectionState.CONNECTED
        f.transport.receive(SessionState("sharing", "r_1", "민지"), "s_1")
        runCurrent()
        f.manager.updateOwnLocation(FIX)
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(1, f.transport.sent.size)
        f.manager.reconnect()
        runCurrent()
        assertEquals(listOf("token_1", "token_1"), f.transport.tokens)
        assertFalse(f.state.synchronized)
        sharing(f)
        assertNull(f.state.ownLocation)
        f.manager.updateOwnLocation(FIX)
        assertEquals(2, f.transport.sent.size)
        assertTrue(f.transport.sent.none { it is SessionEnd })
    }

    @Test
    fun `connected but unsynchronized app closed sends no session end`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.reconnect()
        runCurrent()
        f.transport.connection.value = ConnectionState.CONNECTED
        runCurrent()
        f.manager.appClosed()
        assertTrue(f.transport.sent.isEmpty())
        assertTrue(f.transport.closeCalls > 0)
    }

    @Test
    fun `app closed cancels a pending pickup deadline without later reconnect`() = runTest {
        val f = fixture()
        ready(f)
        f.manager.requestPickup()
        runCurrent()
        f.manager.appClosed()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(listOf<ClientMessage>(PickupRequest("민지"), SessionEnd("app_closed")), f.transport.sent)
        assertEquals(listOf("token_1"), f.transport.tokens)
        assertFalse(f.state.isActive)
    }

    @Test
    fun `manager close clears GPS and ignores late fixes without cancelling its parent scope`() = runTest {
        val f = fixture()
        sharing(f)
        f.manager.updateOwnLocation(FIX)
        runCurrent()
        f.manager.close()
        assertNull(f.state.ownLocation)
        val closed = f.state
        f.manager.updateOwnLocation(FIX)
        f.manager.setLocationNotice("late callback")
        f.manager.reconnect()
        advanceTimeBy(9_000)
        runCurrent()
        assertEquals(closed, f.state)
        assertEquals(1, f.transport.sent.size)
    }

    private fun TestScope.fixture(
        pairing: StoredPairing? = PAIRING,
        snapshotOnConnect: Received? = null,
    ): Fixture {
        val api = FakePairingApi()
        val store = MemoryPairingStore(pairing)
        val transport = FakeSessionTransport(snapshotOnConnect)
        val manager = PhoneSessionManager(api, store, transport, backgroundScope)
        return Fixture(api, store, transport, manager)
    }

    private fun TestScope.ready(f: Fixture) {
        runCurrent()
        f.transport.connection.value = ConnectionState.CONNECTED
        runCurrent()
        f.transport.receive(SessionState("none"), null)
        runCurrent()
    }

    private fun TestScope.sharing(f: Fixture, sessionId: String = "s_1") {
        runCurrent()
        f.transport.connection.value = ConnectionState.CONNECTED
        runCurrent()
        f.transport.receive(SessionState("sharing", "r_1", "민지"), sessionId)
        runCurrent()
        assertEquals(PhonePhase.SHARING, f.state.phase)
    }

    private data class Fixture(
        val api: FakePairingApi,
        val store: MemoryPairingStore,
        val transport: FakeSessionTransport,
        val manager: PhoneSessionManager,
    ) {
        val state: LivePhoneState get() = manager.state.value
    }

    private class FakePairingApi : PairingApi {
        val requests = mutableListOf<Pair<String, String>>()
        var response: suspend () -> PairingResult = { PairingResult("r_1", "v_1", "token_1") }

        override suspend fun pair(code: String, riderName: String): PairingResult {
            requests += code to riderName
            return response()
        }
    }

    private class MemoryPairingStore(var saved: StoredPairing?) : PairingStore {
        override fun load(): StoredPairing? = saved
        override fun save(pairing: StoredPairing) { saved = pairing }
        override fun clear() { saved = null }
    }

    private class FakeSessionTransport(private val snapshotOnConnect: Received?) : SessionTransport {
        override val incoming = MutableSharedFlow<Received>(extraBufferCapacity = 32)
        override val connection = DeferredConnectionState()
        val tokens = mutableListOf<String>()
        val sent = mutableListOf<ClientMessage>()
        var closeCalls = 0
        var sendSucceeds = true

        override fun connect(token: String) {
            tokens += token
            connection.value = ConnectionState.CONNECTING
            snapshotOnConnect?.let {
                connection.value = ConnectionState.CONNECTED
                check(incoming.tryEmit(it))
            }
        }

        override fun send(message: ClientMessage): Boolean {
            if (!sendSucceeds || connection.value != ConnectionState.CONNECTED) return false
            sent += message
            return true
        }

        override fun close() { closeCalls++ }

        fun receive(message: Message, sessionId: String?) {
            check(incoming.tryEmit(Received(message, sessionId, 1)))
        }
    }

    /** Separates a socket's live status from when the manager observes its notification. */
    private class DeferredConnectionState(
        private val notifications: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.DISCONNECTED),
    ) : StateFlow<ConnectionState> by notifications {
        private var latest = ConnectionState.DISCONNECTED

        override var value: ConnectionState
            get() = latest
            set(value) {
                latest = value
                notifications.value = value
            }

        fun deferNotification(value: ConnectionState) { latest = value }
        fun publishLatest() { notifications.value = latest }
    }

    private companion object {
        val PAIRING = StoredPairing("r_1", "v_1", "token_1", "민지")
        val FIX = LocationFix(37.4983, 127.0628, 4f)
    }
}
