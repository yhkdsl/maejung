package com.maejung.phone.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LocationCourseTest {
    @Test fun `fresh approximate fix is accepted without inventing travel course`() {
        val fix = LocationFix(37.5, 127.0, 100f, timestampMillis = 100_000)
        assertEquals(fix, LocationCourse().accept(fix, 105_000))
    }

    @Test fun `stale or future last known fixes are rejected`() {
        val course = LocationCourse()
        assertNull(course.accept(fix(timestamp = 89_999), 100_000))
        assertNull(course.accept(fix(timestamp = 102_000), 100_000))
    }

    @Test fun `reported GPS bearing supplies course without device orientation`() {
        assertEquals(90f, LocationCourse().accept(fix(course = 90f), 100_000)?.courseDegrees)
    }

    @Test fun `eastward GPS displacement supplies eastward travel course`() {
        val course = LocationCourse()
        course.accept(fix(), 100_000)
        val next = assertNotNull(course.accept(fix(lng = 0.001, timestamp = 103_000), 103_000))
        assertEquals(90f, next.courseDegrees!!, 0.1f)
    }

    @Test fun `stationary jitter below reported accuracy leaves course unknown`() {
        val course = LocationCourse()
        course.accept(fix(), 100_000)
        assertNull(assertNotNull(course.accept(fix(lng = 0.00001, timestamp = 103_000), 103_000)).courseDegrees)
    }

    @Test fun `reset prevents deriving a new sessions course from the previous session`() {
        val course = LocationCourse()
        course.accept(fix(), 100_000)
        course.reset()
        assertNull(assertNotNull(course.accept(fix(lng = 0.001, timestamp = 103_000), 103_000)).courseDegrees)
    }

    @Test fun `invalid fix coordinates or accuracy cannot seed course`() {
        val course = LocationCourse()
        assertNull(course.accept(fix().copy(lat = Double.NaN), 100_000))
        assertNull(course.accept(fix().copy(accuracy = -1f), 100_000))
        assertNull(course.accept(fix().copy(lng = 181.0), 100_000))
    }

    private fun fix(lng: Double = 0.0, timestamp: Long = 100_000, course: Float? = null) =
        LocationFix(0.0, lng, 5f, course, timestamp)
}
