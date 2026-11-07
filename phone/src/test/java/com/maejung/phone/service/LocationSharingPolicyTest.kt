package com.maejung.phone.service

import com.maejung.phone.net.ConnectionState
import com.maejung.phone.session.PhonePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocationSharingPolicyTest {
    @Test fun `approximate permission is a valid location grant`() {
        assertTrue(LocationPermissions(coarse = true).granted)
        assertTrue(LocationPermissions(fine = true).granted)
        assertFalse(LocationPermissions().granted)
    }

    @Test fun `pickup click without permission still sends request and asks for location`() {
        val decision = pickupLocationDecision(LocationPermissions())
        assertTrue(decision.sendPickup)
        assertTrue(decision.requestPermission)
        assertNotNull(decision.notice)
    }

    @Test fun `approximate grant sends pickup without another permission request`() {
        val decision = pickupLocationDecision(LocationPermissions(coarse = true))
        assertTrue(decision.sendPickup)
        assertFalse(decision.requestPermission)
        assertNull(decision.notice)
    }

    @Test fun `sharing in resumed activity starts a service`() {
        assertEquals(LocationServiceAction.START, decide(activityResumed = true).action)
    }

    @Test fun `background acceptance waits for resumed activity rather than launching FGS`() {
        val result = decide(activityResumed = false)
        assertEquals(LocationServiceAction.WAIT, result.action)
        assertNotNull(result.notice)
    }

    @Test fun `existing same session service survives screen down`() {
        assertEquals(LocationServiceAction.KEEP, decide(activityResumed = false, running = "s1").action)
    }

    @Test fun `revoked location stops existing service with a notice`() {
        val result = decide(permissions = LocationPermissions(), running = "s1")
        assertEquals(LocationServiceAction.STOP, result.action)
        assertNotNull(result.notice)
    }

    @Test fun `disabled GPS stops subscription even with coarse permission`() {
        val result = decide(gpsEnabled = false, running = "s1")
        assertEquals(LocationServiceAction.STOP, result.action)
        assertNotNull(result.notice)
    }

    @Test fun `no phase outside sharing may run a location service`() {
        for (phase in PhonePhase.entries.filter { it != PhonePhase.SHARING }) {
            assertEquals(LocationServiceAction.STOP, decide(phase = phase, running = "s1").action, phase.name)
        }
        assertEquals(LocationServiceAction.STOP, decide(sessionId = null).action)
    }

    @Test fun `a replacement sharing session in background cannot inherit the old service`() {
        assertEquals(LocationServiceAction.WAIT, decide(activityResumed = false, running = "old").action)
    }

    @Test fun `terminal replacement or unauthorized connection stops GPS despite retained sharing phase`() {
        for (connection in listOf(ConnectionState.REPLACED, ConnectionState.UNAUTHORIZED)) {
            assertEquals(LocationServiceAction.STOP, decide(running = "s1", connection = connection).action, connection.name)
        }
    }

    @Test fun `ordinary disconnection preserves the same sessions running GPS`() {
        assertEquals(LocationServiceAction.KEEP,
            decide(activityResumed = false, running = "s1", connection = ConnectionState.DISCONNECTED).action)
    }

    private fun decide(
        phase: PhonePhase = PhonePhase.SHARING,
        sessionId: String? = "s1",
        permissions: LocationPermissions = LocationPermissions(coarse = true),
        gpsEnabled: Boolean = true,
        activityResumed: Boolean = true,
        running: String? = null,
        connection: ConnectionState = ConnectionState.CONNECTED,
    ) = locationServiceDecision(phase, sessionId, permissions, gpsEnabled, activityResumed, running, connection)
}
