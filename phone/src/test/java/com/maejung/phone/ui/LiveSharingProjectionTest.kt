package com.maejung.phone.ui

import com.maejung.phone.location.LocationFix
import com.maejung.phone.session.LivePhoneState
import com.maejung.phone.session.PhonePhase
import com.maejung.protocol.PeerEta
import com.maejung.protocol.PeerLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveSharingProjectionTest {
    @Test
    fun `distance is the separation of own and received WGS84 fixes instead of route meters`() {
        val info = project(own = LocationFix(0.0, 0.0, 5f, 35f), car = PeerLocation(0.0, .001, 5f),
            eta = PeerEta(77, 890, "navi"))
        assertEquals(111, info.distanceMeters)
        assertEquals(90, info.bearingDegrees)
        assertEquals(35, info.courseDegrees)
        assertEquals(55, relativeCarAngle(info.bearingDegrees, info.courseDegrees))
        assertEquals(77, info.etaSeconds)
        assertEquals("navi", info.etaSource)
    }

    @Test
    fun `academy to parked car agrees with the actual demonstration coordinates`() {
        val info = project(own = LocationFix(37.49830, 127.06280, 5f),
            car = PeerLocation(37.49950, 127.06030, 5f))
        assertTrue(assertNotNull(info.distanceMeters) in 255..260)
        assertTrue(assertNotNull(info.bearingDegrees) in 300..302)
        assertNull(info.courseDegrees)
        assertNull(relativeCarAngle(info.bearingDegrees, info.courseDegrees))
    }

    @Test
    fun `missing course keeps GPS distance and bearing but never invents a compass arrow`() {
        val info = project(own = LocationFix(0.0, 0.0, 5f), car = PeerLocation(.001, 0.0, 5f))
        assertEquals(111, info.distanceMeters)
        assertEquals(0, info.bearingDegrees)
        assertNull(info.courseDegrees)
        assertNull(relativeCarAngle(info.bearingDegrees, info.courseDegrees))
    }

    @Test
    fun `course crossing north wraps the relative arrow to the nearest signed angle`() {
        val info = project(own = LocationFix(0.0, 0.0, 5f, 350f), car = PeerLocation(.001, 0.0, 5f))
        assertEquals(10, relativeCarAngle(info.bearingDegrees, info.courseDegrees))
    }

    @Test
    fun `coincident fixes have zero distance but no meaningful car bearing`() {
        val info = project(own = LocationFix(37.5, 127.1, 5f, 20f), car = PeerLocation(37.5, 127.1, 5f))
        assertEquals(0, info.distanceMeters)
        assertNull(info.bearingDegrees)
        assertNull(relativeCarAngle(info.bearingDegrees, info.courseDegrees))
    }

    @Test
    fun `received ETA remains visible without GPS and is never inferred from separation`() {
        val etaOnly = project(eta = PeerEta(0, 890, "estimate"), light = true)
        assertEquals(0, etaOnly.etaSeconds)
        assertEquals("estimate", etaOnly.etaSource)
        assertEquals(true, etaOnly.lightOn)
        assertNull(etaOnly.distanceMeters)
        val gpsOnly = project(own = LocationFix(0.0, 0.0, 5f), car = PeerLocation(0.0, .001, 5f))
        assertNull(gpsOnly.etaSeconds)
        assertNull(gpsOnly.etaSource)
    }

    @Test
    fun `invalid received ETA remains unknown rather than deriving a replacement`() {
        val info = project(eta = PeerEta(-1, 120, "navi"))
        assertNull(info.etaSeconds)
        assertNull(info.etaSource)
    }

    @Test
    fun `missing own or car fix leaves distance and compass unknown despite received route meters`() {
        for ((own, car) in listOf(
            null to PeerLocation(0.0, .001, 5f), LocationFix(0.0, 0.0, 5f, 35f) to null,
        )) {
            val info = project(own, car, PeerEta(90, 120, "estimate"))
            assertNull(info.distanceMeters)
            assertNull(info.bearingDegrees)
            assertNull(relativeCarAngle(info.bearingDegrees, info.courseDegrees))
            assertEquals(90, info.etaSeconds)
        }
    }

    @Test
    fun `nonfinite or out of range coordinates never enter telemetry or map markers`() {
        for (point in listOf(SharingMapPoint(Double.NaN, 0.0), SharingMapPoint(0.0, Double.POSITIVE_INFINITY),
            SharingMapPoint(90.1, 0.0), SharingMapPoint(0.0, -180.1))) {
            val own = LocationFix(point.lat, point.lng, 5f, 35f)
            val car = PeerLocation(point.lat, point.lng, 5f)
            assertNull(project(own, PeerLocation(0.0, 0.0, 5f)).distanceMeters)
            assertNull(project(LocationFix(0.0, 0.0, 5f), car).bearingDegrees)
            val locations = liveSharingMapLocations(LivePhoneState(phase = PhonePhase.SHARING,
                ownLocation = own, peerLocation = car))
            assertNull(locations.rider)
            assertNull(locations.car)
        }
    }

    @Test
    fun `nonfinite or negative accuracy fixes stay unknown at the UI boundary`() {
        for (accuracy in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            val own = LocationFix(0.0, 0.0, accuracy, 35f)
            val car = PeerLocation(0.0, .001, accuracy)
            assertNull(project(own, PeerLocation(0.0, .001, 5f)).distanceMeters)
            assertNull(project(LocationFix(0.0, 0.0, 5f), car).distanceMeters)
            val locations = liveSharingMapLocations(LivePhoneState(phase = PhonePhase.SHARING,
                ownLocation = own, peerLocation = car))
            assertNull(locations.car)
            assertNull(locations.rider)
        }
    }

    @Test
    fun `invalid course keeps valid GPS positions but leaves compass unknown`() {
        for (course in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 360f)) {
            val info = project(LocationFix(0.0, 0.0, 5f, course), PeerLocation(0.0, .001, 5f))
            assertEquals(111, info.distanceMeters)
            assertNull(info.courseDegrees)
        }
    }

    @Test
    fun `live markers use exact received and own coordinates without a preview fallback`() {
        val state = LivePhoneState(phase = PhonePhase.SHARING,
            ownLocation = LocationFix(37.49830, 127.06280, 5f),
            peerLocation = PeerLocation(37.49950, 127.06030, 5f))
        val locations = liveSharingMapLocations(state)
        assertEquals(SharingMapPoint(37.49950, 127.06030), locations.car)
        assertEquals(SharingMapPoint(37.49830, 127.06280), locations.rider)
        assertEquals(SharingMapLocations(car = locations.car), liveSharingMapLocations(state.copy(ownLocation = null)))
        assertEquals(SharingMapLocations(rider = locations.rider), liveSharingMapLocations(state.copy(peerLocation = null)))
        assertEquals(SharingMapLocations(), liveSharingMapLocations(state.copy(ownLocation = null, peerLocation = null)))
    }

    @Test
    fun `nonsharing phases suppress markers even when old locations remain in state`() {
        for (phase in PhonePhase.entries.filter { it != PhonePhase.SHARING }) {
            assertEquals(SharingMapLocations(), liveSharingMapLocations(LivePhoneState(phase = phase,
                ownLocation = LocationFix(37.49830, 127.06280, 5f),
                peerLocation = PeerLocation(37.49950, 127.06030, 5f))))
        }
    }

    private fun project(own: LocationFix? = null, car: PeerLocation? = null,
        eta: PeerEta? = null, light: Boolean? = null) = projectLiveSharingTelemetry(own, car, eta, light)
}
