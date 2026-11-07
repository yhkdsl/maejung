package com.maejung.phone.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle distance in meters for WGS84 GPS coordinates. */
fun gpsDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val halfLatitude = sin((phi2 - phi1) / 2)
    val halfLongitude = sin(Math.toRadians(lng2 - lng1) / 2)
    val haversine = (halfLatitude * halfLatitude +
        cos(phi1) * cos(phi2) * halfLongitude * halfLongitude).coerceIn(0.0, 1.0)
    return 6_371_000.0 * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
}

/** Initial WGS84 bearing clockwise from north, in [0, 360). */
fun gpsBearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLongitude = Math.toRadians(lng2 - lng1)
    val y = sin(deltaLongitude) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLongitude)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Prefer GNSS course; otherwise require displacement above the GPS accuracy or five meters. */
fun gpsCourseDegrees(previous: LocationFix?, current: LocationFix): Float? {
    current.courseDegrees?.takeIf { it.isFinite() }?.let {
        return ((it.toDouble() % 360.0 + 360.0) % 360.0).toFloat()
    }
    if (previous == null) return null
    val distance = gpsDistanceMeters(previous.lat, previous.lng, current.lat, current.lng)
    val minimumDisplacement = maxOf(5.0, previous.accuracy.toDouble(), current.accuracy.toDouble())
    if (!distance.isFinite() || !minimumDisplacement.isFinite() || distance <= minimumDisplacement) return null
    return gpsBearingDegrees(previous.lat, previous.lng, current.lat, current.lng).toFloat()
}
