package com.maejung.phone.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeoMathTest {
    @Test
    fun `same GPS point has zero distance`() {
        assertEquals(0.0, gpsDistanceMeters(37.4983, 127.0628, 37.4983, 127.0628), 0.001)
    }

    @Test
    fun `distance uses spherical meters and crosses the date line`() {
        assertEquals(111_195.0, gpsDistanceMeters(0.0, 0.0, 0.0, 1.0), 1.0)
        assertEquals(22_239.0, gpsDistanceMeters(0.0, 179.9, 0.0, -179.9), 1.0)
    }

    @Test
    fun `GPS bearings are clockwise from north`() {
        assertEquals(0.0, gpsBearingDegrees(0.0, 0.0, 1.0, 0.0), 0.001)
        assertEquals(90.0, gpsBearingDegrees(0.0, 0.0, 0.0, 1.0), 0.001)
        assertEquals(180.0, gpsBearingDegrees(0.0, 0.0, -1.0, 0.0), 0.001)
        assertEquals(270.0, gpsBearingDegrees(0.0, 0.0, 0.0, -1.0), 0.001)
    }

    @Test
    fun `GNSS course takes precedence over GPS displacement`() {
        val previous = LocationFix(0.0, 0.0, 3f)
        val current = LocationFix(0.0, 0.001, 3f, courseDegrees = 25f)
        assertEquals(25f, gpsCourseDegrees(previous, current))
    }

    @Test
    fun `GPS displacement supplies course when GNSS has none`() {
        val previous = LocationFix(0.0, 0.0, 3f)
        val current = LocationFix(0.0, 0.001, 3f)
        assertEquals(90f, requireNotNull(gpsCourseDegrees(previous, current)), 0.01f)
    }

    @Test
    fun `stationary or missing previous GPS leaves course unknown`() {
        val fix = LocationFix(37.4983, 127.0628, 3f)
        assertNull(gpsCourseDegrees(null, fix))
        assertNull(gpsCourseDegrees(fix, fix))
    }
}
