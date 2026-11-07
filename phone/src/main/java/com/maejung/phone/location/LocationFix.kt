package com.maejung.phone.location

/** Framework-free WGS84 fix shared by session, platform, and map code. */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val courseDegrees: Float? = null,
    val timestampMillis: Long = 0L,
)
