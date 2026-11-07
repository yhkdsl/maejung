package com.maejung.phone.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReportedGpsCourseTest {
    @Test fun `stationary GPS reported bearing zero is unknown course`() {
        assertNull(reportedGpsCourse(0f, 0f, 0.5f))
    }

    @Test fun `missing or slow speed cannot validate a reported GPS course`() {
        assertNull(reportedGpsCourse(90f, null))
        assertNull(reportedGpsCourse(90f, 0.1f))
        assertNull(reportedGpsCourse(90f, 0.5f))
    }

    @Test fun `moving GPS accepts a valid bearing including north zero`() {
        assertEquals(90f, reportedGpsCourse(90f, 2f, 0.5f))
        assertEquals(0f, reportedGpsCourse(0f, 2f, 0.5f))
        assertEquals(359.9f, reportedGpsCourse(359.9f, 2f))
    }

    @Test fun `speed within reported uncertainty cannot validate GPS bearing`() {
        assertNull(reportedGpsCourse(90f, 1f, 1f))
        assertNull(reportedGpsCourse(90f, 1f, 2f))
    }

    @Test fun `missing nonfinite or out of range bearings stay unknown`() {
        for (bearing in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 360f)) {
            assertNull(reportedGpsCourse(bearing, 2f), "bearing=$bearing")
        }
    }

    @Test fun `negative or nonfinite speed cannot validate a bearing`() {
        for (speed in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(reportedGpsCourse(90f, speed), "speed=$speed")
        }
    }

    @Test fun `invalid speed uncertainty cannot validate a bearing`() {
        for (accuracy in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(reportedGpsCourse(90f, 2f, accuracy), "speedAccuracy=$accuracy")
        }
    }

    @Test fun `stationary reported bearing zero leaves the initial course unknown`() {
        val course = LocationCourse()
        val fix = LocationFix(0.0, 0.0, 5f, reportedGpsCourse(0f, 0f, 0.5f), 100_000)
        assertNull(assertNotNull(course.accept(fix, 100_000)).courseDegrees)
    }

    @Test fun `stationary reported zero bearing allows northwest displacement course fallback`() {
        val course = LocationCourse()
        course.accept(LocationFix(0.0, 0.0, 5f, reportedGpsCourse(0f, 0f, 0.5f), 100_000), 100_000)
        val moving = LocationFix(0.0006, -0.001, 5f, reportedGpsCourse(0f, 0f, 0.5f), 103_000)
        val result = assertNotNull(course.accept(moving, 103_000))
        assertEquals(301f, assertNotNull(result.courseDegrees), 0.1f)
    }
}
