package com.maejung.phone.location

/** A reported bearing describes travel only when GPS speed establishes meaningful movement. */
fun reportedGpsCourse(
    bearingDegrees: Float?,
    speedMetersPerSecond: Float?,
    speedAccuracyMetersPerSecond: Float? = null,
): Float? {
    if (bearingDegrees == null || !bearingDegrees.isFinite() || bearingDegrees !in 0f..<360f ||
        speedMetersPerSecond == null || !speedMetersPerSecond.isFinite() || speedMetersPerSecond <= 0.5f
    ) return null
    if (speedAccuracyMetersPerSecond != null &&
        (!speedAccuracyMetersPerSecond.isFinite() || speedAccuracyMetersPerSecond < 0f ||
            speedMetersPerSecond <= speedAccuracyMetersPerSecond)
    ) return null
    return bearingDegrees
}

/** Only GPS bearing or movement between GPS fixes can supply travel course. */
class LocationCourse {
    private var previous: LocationFix? = null

    fun accept(fix: LocationFix, nowMillis: Long): LocationFix? {
        if (!fix.lat.isFinite() || fix.lat !in -90.0..90.0 ||
            !fix.lng.isFinite() || fix.lng !in -180.0..180.0 ||
            !fix.accuracy.isFinite() || fix.accuracy < 0f ||
            fix.timestampMillis <= 0 || nowMillis - fix.timestampMillis !in -1_000L..10_000L
        ) return null
        val rawFix = fix.copy(courseDegrees = fix.courseDegrees?.takeIf { it.isFinite() && it in 0f..<360f })
        val anchor = previous?.takeIf { fix.timestampMillis - it.timestampMillis in 0L..30_000L }
        val course = gpsCourseDegrees(anchor, rawFix)
        // Retain the anchor through small GPS jitter so walking can accumulate displacement.
        if (anchor == null || course != null) previous = rawFix
        return rawFix.copy(courseDegrees = course)
    }

    fun reset() { previous = null }
}
