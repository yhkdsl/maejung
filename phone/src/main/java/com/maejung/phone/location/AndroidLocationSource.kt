package com.maejung.phone.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.maejung.phone.service.LOCATION_GPS_NOTICE
import com.maejung.phone.service.LOCATION_PERMISSION_NOTICE
import com.maejung.phone.service.LOCATION_START_NOTICE
import com.maejung.phone.service.SharingLocationSource
import com.maejung.phone.service.gpsEnabled
import com.maejung.phone.service.locationPermissions

/** GPS-only subscription; the service is the sole owner of its lifetime. */
class AndroidLocationSource(private val context: Context) : SharingLocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val course = LocationCourse()
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission") // Rechecked before subscription and each callback; races are caught.
    override fun start(onFix: (LocationFix) -> Unit, onUnavailable: (String) -> Unit) {
        stop()
        if (!locationPermissions(context).granted) {
            onUnavailable(LOCATION_PERMISSION_NOTICE)
            return
        }
        if (manager == null || !gpsEnabled(context)) {
            onUnavailable(LOCATION_GPS_NOTICE)
            return
        }
        val subscription = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (listener !== this) return
                if (!locationPermissions(context).granted) {
                    stop()
                    onUnavailable(LOCATION_PERMISSION_NOTICE)
                    return
                }
                if (!gpsEnabled(context)) {
                    stop()
                    onUnavailable(LOCATION_GPS_NOTICE)
                    return
                }
                if (location.provider != LocationManager.GPS_PROVIDER || !location.hasAccuracy()) return
                val now = System.currentTimeMillis()
                val timestamp = if (location.elapsedRealtimeNanos > 0) {
                    val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                    if (ageNanos < 0) return
                    now - ageNanos / 1_000_000L
                } else location.time
                course.accept(LocationFix(
                    lat = location.latitude,
                    lng = location.longitude,
                    accuracy = location.accuracy,
                    courseDegrees = reportedGpsCourse(
                        bearingDegrees = if (location.hasBearing()) location.bearing else null,
                        speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
                        speedAccuracyMetersPerSecond = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else null,
                    ),
                    timestampMillis = timestamp,
                ), now)?.let(onFix)
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER && listener === this) {
                    stop()
                    onUnavailable(LOCATION_GPS_NOTICE)
                }
            }

            override fun onProviderEnabled(provider: String) = Unit
            @Deprecated("Required by older Android LocationListener implementations")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        listener = subscription
        try {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(subscription::onLocationChanged)
            if (listener === subscription) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3_000L, 0f, subscription, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            stop()
            onUnavailable(LOCATION_PERMISSION_NOTICE)
        } catch (_: RuntimeException) {
            stop()
            onUnavailable(LOCATION_START_NOTICE)
        }
    }

    override fun stop() {
        val old = listener
        listener = null
        course.reset()
        if (old != null) {
            try { manager?.removeUpdates(old) } catch (_: RuntimeException) { /* Permission may have been revoked. */ }
        }
    }
}
