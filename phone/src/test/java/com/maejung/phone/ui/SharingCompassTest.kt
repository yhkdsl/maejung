package com.maejung.phone.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharingCompassTest {
    @Test
    fun `car straight ahead follows travel course rather than map north`() {
        assertEquals(0, relativeCarAngle(bearingDegrees = 80, courseDegrees = 80))
    }

    @Test
    fun `car to the right has a positive clockwise angle`() {
        assertEquals(90, relativeCarAngle(bearingDegrees = 125, courseDegrees = 35))
        assertEquals(45, relativeCarAngle(bearingDegrees = 80, courseDegrees = 35))
    }

    @Test
    fun `car to the left has a negative angle`() {
        assertEquals(-90, relativeCarAngle(bearingDegrees = 305, courseDegrees = 35))
    }

    @Test
    fun `car behind uses the negative half turn boundary`() {
        assertEquals(-180, relativeCarAngle(bearingDegrees = 215, courseDegrees = 35))
    }

    @Test
    fun `crossing north and complete rotations preserve the shortest relative angle`() {
        assertEquals(20, relativeCarAngle(bearingDegrees = 10, courseDegrees = 350))
        assertEquals(-20, relativeCarAngle(bearingDegrees = 350, courseDegrees = 10))
        assertEquals(45, relativeCarAngle(bearingDegrees = 440, courseDegrees = -325))
    }

    @Test
    fun `unknown bearing or course never invents a direction`() {
        assertNull(relativeCarAngle(bearingDegrees = null, courseDegrees = 35))
        assertNull(relativeCarAngle(bearingDegrees = 80, courseDegrees = null))
        assertNull(relativeCarAngle(bearingDegrees = null, courseDegrees = null))
    }

    @Test
    fun `arrival under a minute retains seconds`() {
        assertEquals("약 30초", sharingEtaLabel(30))
        assertEquals("약 59초", sharingEtaLabel(59))
    }

    @Test
    fun `arrival rounds minutes up and leaves unknown time neutral`() {
        assertEquals("약 1분", sharingEtaLabel(60))
        assertEquals("약 2분", sharingEtaLabel(90))
        assertEquals("—", sharingEtaLabel(null))
        assertEquals("—", sharingEtaLabel(-1))
    }
}
