package com.maejung.phone.ui

/** Clockwise angle from direction of travel, in [-180, 180); no device orientation fallback. */
internal fun relativeCarAngle(bearingDegrees: Int?, courseDegrees: Int?): Int? {
    if (bearingDegrees == null || courseDegrees == null) return null
    val difference = bearingDegrees.toLong() - courseDegrees.toLong()
    return (((difference + 180L) % 360L + 360L) % 360L - 180L).toInt()
}

internal fun sharingEtaLabel(etaSeconds: Int?): String = when {
    etaSeconds == null || etaSeconds < 0 -> "—"
    etaSeconds < 60 -> "약 ${etaSeconds}초"
    else -> "약 ${(etaSeconds.toLong() + 59L) / 60L}분"
}
