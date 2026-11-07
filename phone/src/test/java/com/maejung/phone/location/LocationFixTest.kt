package com.maejung.phone.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocationFixTest {
    @Test
    fun `location fix preserves neutral public defaults`() {
        val fix = LocationFix(37.4983, 127.0628, 5f)
        assertEquals(37.4983, fix.lat)
        assertEquals(127.0628, fix.lng)
        assertEquals(5f, fix.accuracy)
        assertNull(fix.courseDegrees)
        assertEquals(0L, fix.timestampMillis)
    }
}
