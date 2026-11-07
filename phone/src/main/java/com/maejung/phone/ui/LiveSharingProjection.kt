package com.maejung.phone.ui

import com.maejung.phone.location.LocationFix
import com.maejung.phone.location.gpsBearingDegrees
import com.maejung.phone.location.gpsDistanceMeters
import com.maejung.phone.session.LivePhoneState
import com.maejung.phone.session.PhonePhase
import com.maejung.phone.session.SharingInfo
import com.maejung.protocol.PeerEta
import com.maejung.protocol.PeerLocation
import kotlin.math.roundToInt

/** WGS84 coordinates shared by the live map and its telemetry projection. */
internal data class SharingMapPoint(val lat: Double, val lng: Double)

internal data class SharingMapLocations(
    val car: SharingMapPoint? = null,
    val rider: SharingMapPoint? = null,
)

/** GPS separation and course; arrival time comes exclusively from received car telemetry. */
internal fun projectLiveSharingTelemetry(
    ownLocation: LocationFix?,
    carLocation: PeerLocation?,
    eta: PeerEta?,
    lightOn: Boolean?,
): SharingInfo {
    val own = ownLocation?.takeIf { validMapFix(it.lat, it.lng, it.accuracy) }
    val car = carLocation?.takeIf { validMapFix(it.lat, it.lng, it.accuracy) }
    val distance = if (own != null && car != null) {
        gpsDistanceMeters(own.lat, own.lng, car.lat, car.lng)
    } else null
    val bearing = if (own != null && car != null && distance != null && distance > 0.0) {
        gpsBearingDegrees(own.lat, own.lng, car.lat, car.lng).roundToInt() % 360
    } else null
    val seconds = eta?.seconds?.takeIf { it >= 0 }
    return SharingInfo(
        distanceMeters = distance?.roundToInt(),
        bearingDegrees = bearing,
        courseDegrees = own?.courseDegrees?.takeIf { it.isFinite() && it >= 0f && it < 360f }
            ?.roundToInt()?.rem(360),
        etaSeconds = seconds,
        etaSource = eta?.source?.takeIf { seconds != null },
        lightOn = lightOn,
    )
}

internal fun liveSharingMapLocations(state: LivePhoneState): SharingMapLocations {
    if (state.phase != PhonePhase.SHARING) return SharingMapLocations()
    return SharingMapLocations(
        car = state.peerLocation?.takeIf { validMapFix(it.lat, it.lng, it.accuracy) }
            ?.let { SharingMapPoint(it.lat, it.lng) },
        rider = state.ownLocation?.takeIf { validMapFix(it.lat, it.lng, it.accuracy) }
            ?.let { SharingMapPoint(it.lat, it.lng) },
    )
}

private fun validMapFix(lat: Double, lng: Double, accuracy: Float): Boolean =
    lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0 &&
        accuracy.isFinite() && accuracy >= 0f
